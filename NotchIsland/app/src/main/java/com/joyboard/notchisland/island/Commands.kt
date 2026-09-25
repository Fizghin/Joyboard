package com.joyboard.notchisland.island

enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS }

enum class TimerCommand { PAUSE, RESUME, ADD_MINUTE, CANCEL }

enum class StopwatchCommand { START_PAUSE, LAP, RESET }

enum class QuickToggle(val label: String) {
    TORCH("Flashlight"),
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    DND("Do Not Disturb"),
    ROTATION("Auto-rotate"),
    RINGER("Ringer mode"),
    SETTINGS("System settings"),
    APP_SETTINGS("Notch Island settings"),
}
