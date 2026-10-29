package com.zero.assistant

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal data class OrganicOrbMesh(
    val vertices: FloatArray,
    val edges: IntArray,
    val particles: FloatArray,
)

internal object OrganicOrbGeometry {
    fun create(subdivision: Int = 3): OrganicOrbMesh {
        val vertices = mutableListOf<FloatArray>()
        val faces = mutableListOf<IntArray>()
        val phi = (1.0f + kotlin.math.sqrt(5.0f)) / 2.0f
        fun add(x: Float, y: Float, z: Float): Int {
            val length = kotlin.math.sqrt(x * x + y * y + z * z)
            vertices += floatArrayOf(x / length, y / length, z / length)
            return vertices.lastIndex
        }
        val n = -1.0f / phi
        val p = 1.0f / phi
        val a = add(-1f, n, 0f); val b = add(1f, n, 0f)
        val c = add(-1f, p, 0f); val d = add(1f, p, 0f)
        val e = add(n, 0f, -1f); val f = add(n, 0f, 1f)
        val g = add(p, 0f, -1f); val h = add(p, 0f, 1f)
        val i = add(0f, -1f, n); val j = add(0f, 1f, n)
        val k = add(0f, -1f, p); val l = add(0f, 1f, p)
        faces += listOf(
            intArrayOf(a,l,f), intArrayOf(a,f,b), intArrayOf(a,b,h), intArrayOf(a,h,k), intArrayOf(a,k,l),
            intArrayOf(b,f,j), intArrayOf(f,l,e), intArrayOf(l,k,c), intArrayOf(k,h,g), intArrayOf(h,b,i),
            intArrayOf(d,j,e), intArrayOf(d,e,c), intArrayOf(d,c,g), intArrayOf(d,g,i), intArrayOf(d,i,j),
            intArrayOf(e,j,f), intArrayOf(c,e,l), intArrayOf(g,c,k), intArrayOf(i,g,h), intArrayOf(j,i,b),
        )
        repeat(subdivision) {
            val midpointCache = mutableMapOf<Long, Int>()
            fun midpoint(first: Int, second: Int): Int {
                val key = if (first < second) first.toLong() shl 32 or (second.toLong() and 0xffffffffL) else second.toLong() shl 32 or (first.toLong() and 0xffffffffL)
                midpointCache[key]?.let { return it }
                val u = vertices[first]; val v = vertices[second]
                val index = add((u[0] + v[0]) / 2f, (u[1] + v[1]) / 2f, (u[2] + v[2]) / 2f)
                midpointCache[key] = index
                return index
            }
            val next = mutableListOf<IntArray>()
            faces.forEach { face ->
                val ab = midpoint(face[0], face[1]); val bc = midpoint(face[1], face[2]); val ca = midpoint(face[2], face[0])
                next += intArrayOf(face[0], ab, ca)
                next += intArrayOf(face[1], bc, ab)
                next += intArrayOf(face[2], ca, bc)
                next += intArrayOf(ab, bc, ca)
            }
            faces.clear(); faces += next
        }
        val flatVertices = FloatArray(vertices.size * 3)
        vertices.forEachIndexed { index, vertex ->
            flatVertices[index * 3] = vertex[0]; flatVertices[index * 3 + 1] = vertex[1]; flatVertices[index * 3 + 2] = vertex[2]
        }
        val edgeSet = mutableSetOf<Long>()
        faces.forEach { face ->
            for (i in 0..2) {
                val a = face[i]; val b = face[(i + 1) % 3]
                val low = minOf(a, b); val high = maxOf(a, b)
                edgeSet += low.toLong() shl 32 or (high.toLong() and 0xffffffffL)
            }
        }
        val edgeList = mutableListOf<Int>()
        edgeSet.forEach { edge ->
            edgeList += (edge ushr 32).toInt(); edgeList += (edge and 0xffffffffL).toInt()
        }
        val random = Random(170707)
        val particles = FloatArray(48 * 4)
        repeat(48) { index ->
            val offset = index * 4
            val theta = random.nextFloat() * PI.toFloat() * 2f
            val z = random.nextFloat() * 2f - 1f
            val radial = kotlin.math.sqrt(1f - z * z)
            val distance = 1.12f + random.nextFloat() * 0.28f
            particles[offset] = cos(theta) * radial * distance
            particles[offset + 1] = z * distance
            particles[offset + 2] = sin(theta) * radial * distance
            particles[offset + 3] = 0.6f + random.nextFloat() * 0.9f
        }
        return OrganicOrbMesh(flatVertices, edgeList.toIntArray(), particles)
    }
}
