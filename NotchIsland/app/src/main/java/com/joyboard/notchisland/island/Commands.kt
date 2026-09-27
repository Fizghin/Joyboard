package com.joyboard.notchisland.island

import com.joyboard.notchisland.R
import androidx.annotation.StringRes

enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS }

enum class TimerCommand {
    PAUSE, RESUME, ADD_MINUTE, CANCEL,
    /** A finished timer: silence it. */
    STOP_ALARM,
    /** A finished timer: silence it and run the same length again. */
    REPEAT,
}

enum class StopwatchCommand { START_PAUSE, LAP, RESET }

enum class QuickToggle(@StringRes val label: Int) {
    TORCH(R.string.flashlight),
    WIFI(R.string.wi_fi),
    BLUETOOTH(R.string.bluetooth),
    DND(R.string.do_not_disturb),
    ROTATION(R.string.auto_rotate),
    RINGER(R.string.ringer_mode),
    SETTINGS(R.string.system_settings),
    APP_SETTINGS(R.string.notch_island_settings),
}
