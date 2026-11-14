package com.zero.assistant.core

enum class AssistantAction(val label: String) {
    OPEN_ASSISTANT("Open ZERO"),
    OPEN_UPI_SCANNER("Open scanner"),
    OPEN_CAMERA("Open camera"),
    TOGGLE_FLASHLIGHT("Toggle flashlight"),
    AUTO_CLICK_TARGET("Click a spoken screen target"),
    SCREEN_NAVIGATION("Go back"),
    SYSTEM_MEDIA_CONTROL("Toggle media playback"),
    OFFLINE_CALL_SMS("Open dialer for review"),
    EMERGENCY_SOS("Open emergency quick mode"),
    OFFLINE_VOICE_NOTE("Open local notes"),
}

enum class AssistantTrigger(val label: String, val defaultAction: AssistantAction) {
    BACK_TAP_2("Back-tap 2x", AssistantAction.OPEN_UPI_SCANNER),
    BACK_TAP_3("Back-tap 3x", AssistantAction.OPEN_CAMERA),
    BACK_TAP_4("Back-tap 4x", AssistantAction.TOGGLE_FLASHLIGHT),
    SHAKE("Shake", AssistantAction.TOGGLE_FLASHLIGHT),
    VOLUME_UP_DOWN("Volume Up + Down", AssistantAction.OPEN_ASSISTANT),
    VOICE("Voice: “run my voice action”", AssistantAction.AUTO_CLICK_TARGET),
}
