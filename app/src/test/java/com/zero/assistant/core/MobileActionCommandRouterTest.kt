package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileActionCommandRouterTest {
    @Test
    fun routesModelToolCallsThroughExistingReviewedCommands() {
        val commands = MobileActionCommandRouter.toCommands(
            listOf(
                MobileActionToolCall("turn_on_flashlight", emptyMap()),
                MobileActionToolCall(
                    "send_whatsapp_message",
                    mapOf("contact" to "Mamma", "message" to "I am coming home"),
                ),
                MobileActionToolCall("get_weather", mapOf("city" to "Delhi")),
            ),
        )

        assertEquals(
            listOf("torch on", "whatsapp text Mamma: I am coming home", "weather in Delhi"),
            commands,
        )
    }

    @Test
    fun rejectsUnknownToolsAndOversizedPlans() {
        assertNull(MobileActionCommandRouter.toCommands(listOf(MobileActionToolCall("unknown", emptyMap()))))
        assertNull(
            MobileActionCommandRouter.toCommands(
                List(9) { MobileActionToolCall("turn_on_flashlight", emptyMap()) },
            ),
        )
    }

    @Test
    fun rejectsArgumentsThatCouldTurnOneActionIntoACommandChain() {
        assertNull(
            MobileActionCommandRouter.toCommands(
                listOf(MobileActionToolCall("make_call", mapOf("contact" to "Mamma then open camera"))),
            ),
        )
        assertNull(
            MobileActionCommandRouter.toCommands(
                listOf(MobileActionToolCall("open_app_and_search", mapOf("app" to "camera; call 12345678"))),
            ),
        )
    }
}
