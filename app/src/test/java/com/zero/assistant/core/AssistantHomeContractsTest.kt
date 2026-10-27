package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantHomeContractsTest {
    @Test
    fun everyHomeSuggestionMapsToASupportedLocalCommand() {
        val suggestions = HomeSuggestionCatalog.primary + HomeSuggestionCatalog.more

        assertTrue(suggestions.isNotEmpty())
        suggestions.forEach { suggestion ->
            assertTrue("${suggestion.label} must not be a decorative action", CommandParser.parse(suggestion.command) != AssistantCommand.Unsupported)
        }
    }

    @Test
    fun onlineRouteRequiresEveryOptInAndNetworkCondition() {
        val unsupported = AssistantCommand.Unsupported
        assertEquals(AssistantResponseRoute.Local, AssistantResponseRouter.select(unsupported, false, true, true, true))
        assertEquals(AssistantResponseRoute.Local, AssistantResponseRouter.select(unsupported, true, false, true, true))
        assertEquals(AssistantResponseRoute.Local, AssistantResponseRouter.select(unsupported, true, true, true, false))
        assertEquals(AssistantResponseRoute.ConfirmOnline, AssistantResponseRouter.select(unsupported, true, true, true, true))
    }

    @Test
    fun deterministicIdentityAlwaysStaysLocal() {
        assertEquals(
            AssistantResponseRoute.Local,
            AssistantResponseRouter.select(AssistantCommand.Identity, true, true, true, true),
        )
    }
}