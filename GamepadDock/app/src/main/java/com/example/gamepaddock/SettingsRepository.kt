package com.example.gamepaddock

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val SENSITIVITY = floatPreferencesKey("cursor_sensitivity")
        val ACCELERATION_ENABLED = booleanPreferencesKey("acceleration_enabled")
        val CURSOR_SIZE = floatPreferencesKey("cursor_size")
        val LONG_PRESS_DURATION = longPreferencesKey("long_press_duration")

        // Button Mappings
        val BTN_CLICK = intPreferencesKey("btn_click")
        val BTN_LONG_CLICK = intPreferencesKey("btn_long_click")
        val BTN_BACK = intPreferencesKey("btn_back")
        val BTN_HOME = intPreferencesKey("btn_home")
        val BTN_RECENTS = intPreferencesKey("btn_recents")
        val BTN_NOTIFICATIONS = intPreferencesKey("btn_notifications")
        val BTN_QUICK_SETTINGS = intPreferencesKey("btn_quick_settings")
        val BTN_KEYBOARD = intPreferencesKey("btn_keyboard")
        val BTN_DRAG = intPreferencesKey("btn_drag")
        val BTN_SCROLL_MODIFIER = intPreferencesKey("btn_scroll_modifier")

        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val sensitivityFlow: Flow<Float> = context.dataStore.data.map { prefs -> prefs[SENSITIVITY] ?: 1.0f }
    val accelerationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[ACCELERATION_ENABLED] ?: true }
    val cursorSizeFlow: Flow<Float> = context.dataStore.data.map { prefs -> prefs[CURSOR_SIZE] ?: 1.0f }
    val longPressDurationFlow: Flow<Long> = context.dataStore.data.map { prefs -> prefs[LONG_PRESS_DURATION] ?: 600L }
    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[ONBOARDING_COMPLETED] ?: false }

    // Mappings Flow returns a map of KeyCode -> ActionString
    val buttonMappingsFlow: Flow<Map<Int, String>> = context.dataStore.data.map { prefs ->
        mapOf(
            (prefs[BTN_CLICK] ?: KeyEvent.KEYCODE_BUTTON_A) to "CLICK",
            (prefs[BTN_LONG_CLICK] ?: KeyEvent.KEYCODE_BUTTON_B) to "LONG_CLICK",
            (prefs[BTN_BACK] ?: KeyEvent.KEYCODE_BUTTON_X) to "BACK",
            (prefs[BTN_HOME] ?: KeyEvent.KEYCODE_BUTTON_Y) to "HOME",
            (prefs[BTN_RECENTS] ?: KeyEvent.KEYCODE_BUTTON_L1) to "RECENTS",
            (prefs[BTN_NOTIFICATIONS] ?: KeyEvent.KEYCODE_BUTTON_R1) to "NOTIFICATIONS",
            (prefs[BTN_QUICK_SETTINGS] ?: -1) to "QUICK_SETTINGS",
            (prefs[BTN_KEYBOARD] ?: KeyEvent.KEYCODE_BUTTON_START) to "TOGGLE_KEYBOARD",
            (prefs[BTN_DRAG] ?: KeyEvent.KEYCODE_BUTTON_THUMBR) to "DRAG",
            (prefs[BTN_SCROLL_MODIFIER] ?: KeyEvent.KEYCODE_BUTTON_L2) to "SCROLL_MODIFIER"
        )
    }

    suspend fun updateSensitivity(value: Float) {
        context.dataStore.edit { it[SENSITIVITY] = value }
    }

    suspend fun updateAcceleration(enabled: Boolean) {
        context.dataStore.edit { it[ACCELERATION_ENABLED] = enabled }
    }

    suspend fun updateCursorSize(size: Float) {
        context.dataStore.edit { it[CURSOR_SIZE] = size }
    }

    suspend fun updateLongPressDuration(durationMs: Long) {
        context.dataStore.edit { it[LONG_PRESS_DURATION] = durationMs }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = true }
    }

    suspend fun updateButtonMapping(action: String, keyCode: Int) {
        context.dataStore.edit { prefs ->
            // Clear old mapping if exists
            listOf(BTN_CLICK, BTN_LONG_CLICK, BTN_BACK, BTN_HOME, BTN_RECENTS,
                   BTN_NOTIFICATIONS, BTN_QUICK_SETTINGS, BTN_KEYBOARD, BTN_DRAG, BTN_SCROLL_MODIFIER).forEach { key ->
                if (prefs[key] == keyCode) prefs.remove(key)
            }

            // Set new
            when (action) {
                "CLICK" -> prefs[BTN_CLICK] = keyCode
                "LONG_CLICK" -> prefs[BTN_LONG_CLICK] = keyCode
                "BACK" -> prefs[BTN_BACK] = keyCode
                "HOME" -> prefs[BTN_HOME] = keyCode
                "RECENTS" -> prefs[BTN_RECENTS] = keyCode
                "NOTIFICATIONS" -> prefs[BTN_NOTIFICATIONS] = keyCode
                "QUICK_SETTINGS" -> prefs[BTN_QUICK_SETTINGS] = keyCode
                "TOGGLE_KEYBOARD" -> prefs[BTN_KEYBOARD] = keyCode
                "DRAG" -> prefs[BTN_DRAG] = keyCode
                "SCROLL_MODIFIER" -> prefs[BTN_SCROLL_MODIFIER] = keyCode
            }
        }
    }
}
