package com.zero.assistant

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

data class StorageAuditEntry(
    val root: String,
    val relativePath: String,
    val bytes: Long,
    val directory: Boolean,
    val safeToDelete: Boolean,
)

data class StorageAuditReport(
    val rootBytes: Map<String, Long>,
    val largestEntries: List<StorageAuditEntry>,
    val safeCacheFileCount: Int,
    val warnings: List<String>,
)

data class StorageCleanupResult(val deletedFiles: Int, val freedBytes: Long)

object StorageAuditHelper {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_DEPTH = 10
    private const val CACHE_FILE_AGE_MS = 10 * 60 * 1000L
    private const val PARTIAL_FILE_AGE_MS = 24 * 60 * 60 * 1000L

    fun scan(context: Context): StorageAuditReport {
        val roots = buildList {
            add("files" to context.filesDir)
            add("cache" to context.cacheDir)
            context.getExternalFilesDirs(null).forEachIndexed { index, file ->
                if (file != null) add("external-$index" to file)
            }
        }.distinctBy { runCatching { it.second.canonicalPath }.getOrDefault(it.second.absolutePath) }
        val allEntries = mutableListOf<StorageAuditEntry>()
        val rootTotals = linkedMapOf<String, Long>()
        val warnings = mutableListOf<String>()
        var visited = 0

        roots.forEach { (label, root) ->
            val canonicalRoot = runCatching { root.canonicalFile }
                .getOrElse { warnings += "Could not resolve $label storage."; return@forEach }
            if (!canonicalRoot.exists()) {
                rootTotals[label] = 0L
                return@forEach
            }
            fun walk(file: File, depth: Int): Long {
                if (++visited > MAX_ENTRIES) {
                    if (warnings.none { it.startsWith("Scan limit") }) warnings += "Scan limit reached; remaining entries were not inspected."
                    return 0L
                }
                if (runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(true)) return 0L
                val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return 0L
                if (canonical.path != canonicalRoot.path &&
                    !canonical.path.startsWith(canonicalRoot.path + File.separator)
                ) return 0L
                val relative = canonicalRoot.toPath().relativize(canonical.toPath()).toString()
                    .ifBlank { "." }
                if (!file.isDirectory) {
                    val bytes = file.length()
                    allEntries += StorageAuditEntry(
                        label,
                        relative,
                        bytes,
                        directory = false,
                        safeToDelete = label == "cache" && isSafeCacheCandidate(file),
                    )
                    return bytes
                }
                if (depth >= MAX_DEPTH) return 0L
                val children = file.listFiles()
                if (children == null) {
                    warnings += "Could not list $label/$relative."
                    return 0L
                }
                val bytes = children.sumOf { child -> walk(child, depth + 1) }
                allEntries += StorageAuditEntry(label, relative, bytes, directory = true, safeToDelete = false)
                return bytes
            }
            rootTotals[label] = walk(canonicalRoot, 0)
        }
        val ordered = allEntries.sortedByDescending { it.bytes }
        return StorageAuditReport(
            rootTotals,
            ordered.take(40),
            ordered.count { it.safeToDelete && !it.directory },
            warnings.distinct(),
        )
    }

    fun removeSafeCacheFiles(context: Context): StorageCleanupResult {
        val cacheRoot = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return StorageCleanupResult(0, 0)
        var deleted = 0
        var freed = 0L
        cacheRoot.walkTopDown().maxDepth(MAX_DEPTH).forEach { file ->
            if (!file.isFile || runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(true)) return@forEach
            if (!isSafeCacheCandidate(file)) return@forEach
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
            if (!canonical.path.startsWith(cacheRoot.path + File.separator)) return@forEach
            val size = file.length()
            if (file.delete()) {
                deleted++
                freed += size
            }
        }
        return StorageCleanupResult(deleted, freed)
    }

    private fun isSafeCacheCandidate(file: File): Boolean {
        val name = file.name.lowercase()
        val age = System.currentTimeMillis() - file.lastModified()
        return (name.matches(Regex("zero_scan_\\d+\\.jpg")) && age >= CACHE_FILE_AGE_MS) ||
            (age >= PARTIAL_FILE_AGE_MS && listOf(".zip.part", ".tar.bz2.part", ".litertlm.part", ".onnx.part").any(name::endsWith))
    }
}

@Composable
internal fun StorageAuditPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<StorageAuditReport?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        scope.launch { report = withContext(Dispatchers.IO) { StorageAuditHelper.scan(context) } }
    }
    LaunchedEffect(Unit) { refresh() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App-private storage", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lists local files only. Installed speech/action models, notes and Vault data are never removed by this cleanup.",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
            )
            report?.let { current ->
                current.rootBytes.forEach { (root, bytes) ->
                    Text("$root: ${formatBytes(bytes)}", maxLines = 1, softWrap = false)
                }
                Text("Largest entries", style = MaterialTheme.typography.titleSmall)
                current.largestEntries.take(12).forEach { entry ->
                    Text(
                        "${entry.root}/${entry.relativePath} · ${formatBytes(entry.bytes)}${if (entry.safeToDelete) " · stale cache" else ""}",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                current.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::refresh) { Text("Refresh") }
                    Button(
                        enabled = current.safeCacheFileCount > 0,
                        onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    StorageAuditHelper.removeSafeCacheFiles(context)
                                }
                                status = "Removed ${result.deletedFiles} recognized stale cache files (${formatBytes(result.freedBytes)})."
                                refresh()
                            }
                        },
                    ) { Text("Clean safe cache") }
                }
            } ?: Text("Scanning app-private storage…")
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = units[0]
    for (index in 1 until units.size) {
        if (value < 1024) break
        value /= 1024
        unit = units[index]
    }
    return "%.1f %s".format(java.util.Locale.ROOT, value, unit)
}
