package com.zero.assistant.core

import com.zero.assistant.ui.ZeroThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiSequenceTest {
    @Test fun preservesRepresentativeEmojiSequences() {
        val input = listOf("😀", "❤️", "🔥", "👋🏽", "👨‍👩‍👧‍👦", "🚀")
        assertEquals(input, input.map { it })
    }

    @Test fun resolvesPersistedThemeValues() {
        assertEquals(ZeroThemeMode.System, ZeroThemeMode.entries.firstOrNull { it.persistenceValue == "system" })
        assertEquals(ZeroThemeMode.Dark, ZeroThemeMode.entries.firstOrNull { it.persistenceValue == "dark" })
        assertEquals(ZeroThemeMode.Light, ZeroThemeMode.entries.firstOrNull { it.persistenceValue == "light" })
    }
}
