package com.example.gamepaddock

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), InputManager.InputDeviceListener {

    private lateinit var inputManager: InputManager
    private val isControllerConnected = mutableStateOf(false)
    private val controllerName = mutableStateOf("Disconnected")
    private val isServiceEnabled = mutableStateOf(false)

    // Used to intercept key events in the Activity before they reach Compose
    var remapActionId: String? = null
    var onRemapComplete: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        val settingsRepo = SettingsRepository(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val onboardingCompleted by settingsRepo.onboardingCompletedFlow.collectAsState(initial = false)

                    if (!onboardingCompleted) {
                        OnboardingScreen(
                            onComplete = {
                                lifecycleScope.launch { settingsRepo.completeOnboarding() }
                            }
                        )
                    } else {
                        AppContent(
                            settingsRepo = settingsRepo,
                            isServiceEnabled = isServiceEnabled.value,
                            isControllerConnected = isControllerConnected.value,
                            controllerName = controllerName.value,
                            onOpenSettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onStartRemap = { actionId, callback ->
                                remapActionId = actionId
                                onRemapComplete = callback
                            },
                            onCancelRemap = {
                                remapActionId = null
                                onRemapComplete = null
                            },
                            currentRemapActionId = remapActionId
                        )
                    }
                }
            }
        }

        checkStatuses()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (remapActionId != null && event.action == KeyEvent.ACTION_DOWN) {
            onRemapComplete?.invoke(event.keyCode)
            remapActionId = null
            onRemapComplete = null
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        checkStatuses()
    }

    override fun onPause() {
        super.onPause()
        inputManager.unregisterInputDeviceListener(this)
    }

    private fun checkStatuses() {
        // Service
        val expectedComponentName = packageName + "/" + GamepadAccessibilityService::class.java.name
        var enabled = false
        val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (settingValue != null) {
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(settingValue)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    enabled = true
                    break
                }
            }
        }
        isServiceEnabled.value = enabled

        // Controller
        val ids = inputManager.inputDeviceIds
        var connected = false
        var name = "Disconnected"
        for (id in ids) {
            val device = inputManager.getInputDevice(id)
            if (device != null && ((device.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                                   (device.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                connected = true
                name = device.name ?: "Generic Gamepad"
                break
            }
        }
        isControllerConnected.value = connected
        controllerName.value = name
    }

    override fun onInputDeviceAdded(deviceId: Int) { checkStatuses() }
    override fun onInputDeviceRemoved(deviceId: Int) { checkStatuses() }
    override fun onInputDeviceChanged(deviceId: Int) { checkStatuses() }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var step by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (step == 1) {
            Text("Welcome to GamepadDock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text("This app allows you to control your entire tablet using only a physical gamepad.", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text("It provides a mouse cursor, click actions, scrolling, and a custom on-screen keyboard.", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                Text("Next")
            }
        } else if (step == 2) {
            Text("Default Controls", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Left Stick / D-Pad = Move Cursor\nButton A = Click\nButton B = Long Press\nStart/Menu = Toggle Keyboard\nRight Thumb = Hold to Drag\nLeft Trigger = Hold to Scroll", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Text("You can remap these in Settings later.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onComplete() }, modifier = Modifier.fillMaxWidth()) {
                Text("Get Started")
            }
        }
    }
}

@Composable
fun AppContent(
    settingsRepo: SettingsRepository,
    isServiceEnabled: Boolean,
    isControllerConnected: Boolean,
    controllerName: String,
    onOpenSettings: () -> Unit,
    onStartRemap: (String, (Int) -> Unit) -> Unit,
    onCancelRemap: () -> Unit,
    currentRemapActionId: String?
) {
    val coroutineScope = rememberCoroutineScope()
    val sensitivity by settingsRepo.sensitivityFlow.collectAsState(initial = 1.0f)
    val acceleration by settingsRepo.accelerationEnabledFlow.collectAsState(initial = true)
    val longPressDuration by settingsRepo.longPressDurationFlow.collectAsState(initial = 600L)

    val mappings by settingsRepo.buttonMappingsFlow.collectAsState(initial = mapOf())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("GamepadDock", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Controller-based Navigation", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))

        // Status Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accessibility Service: ")
                    Text(if (isServiceEnabled) "Enabled" else "Disabled", color = if (isServiceEnabled) Color(0xFF4CAF50) else Color.Red, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Controller: ")
                    Text(controllerName, color = if (isControllerConnected) Color(0xFF4CAF50) else Color.Red, fontWeight = FontWeight.Bold)
                }

                if (!isServiceEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("OPEN ACCESSIBILITY SETTINGS")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Cursor Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(16.dp))
                Text("Sensitivity: ${"%.1f".format(sensitivity)}x")
                Slider(
                    value = sensitivity,
                    onValueChange = { coroutineScope.launch { settingsRepo.updateSensitivity(it) } },
                    valueRange = 0.1f..3.0f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Long Press Duration: ${longPressDuration}ms")
                Slider(
                    value = longPressDuration.toFloat(),
                    onValueChange = { coroutineScope.launch { settingsRepo.updateLongPressDuration(it.toLong()) } },
                    valueRange = 300f..1500f
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Acceleration", modifier = Modifier.weight(1f))
                    Switch(
                        checked = acceleration,
                        onCheckedChange = { coroutineScope.launch { settingsRepo.updateAcceleration(it) } }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Remapping Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Button Mappings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Tap an action to rebind it.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                val actionLabels = listOf(
                    "CLICK" to "Tap / Click",
                    "LONG_CLICK" to "Long Press",
                    "BACK" to "Go Back",
                    "HOME" to "Home Screen",
                    "RECENTS" to "Recent Apps",
                    "NOTIFICATIONS" to "Notifications",
                    "QUICK_SETTINGS" to "Quick Settings",
                    "TOGGLE_KEYBOARD" to "Show Keyboard",
                    "DRAG" to "Hold to Drag",
                    "SCROLL_MODIFIER" to "Hold to Scroll"
                )

                actionLabels.forEach { (actionId, label) ->
                    val currentKey = mappings.entries.find { it.value == actionId }?.key ?: -1
                    val keyName = if (currentKey == -1) "Unbound" else KeyEvent.keyCodeToString(currentKey).replace("KEYCODE_", "")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            onStartRemap(actionId) { newKey ->
                                coroutineScope.launch { settingsRepo.updateButtonMapping(actionId, newKey) }
                            }
                        }) {
                            Text(keyName)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Test Zone
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Test Zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                var textValue by remember { mutableStateOf("") }
                OutlinedTextField(value = textValue, onValueChange = { textValue = it }, label = { Text("Type here...") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                var checked by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text("Test Checkbox")
                }
                Spacer(modifier = Modifier.height(8.dp))
                var clicks by remember { mutableStateOf(0) }
                Button(onClick = { clicks++ }) {
                    Text("Clicked $clicks times")
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }

    // Remapping Dialog
    if (currentRemapActionId != null) {
        Dialog(onDismissRequest = { onCancelRemap() }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Press any gamepad button", style = MaterialTheme.typography.titleLarge)
                    Text("To bind to: ${currentRemapActionId}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { onCancelRemap() }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
