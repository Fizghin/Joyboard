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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                    AppContent(
                        settingsRepo = settingsRepo,
                        isServiceEnabled = isServiceEnabled.value,
                        isControllerConnected = isControllerConnected.value,
                        controllerName = controllerName.value,
                        onOpenSettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }

        checkStatuses()
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
fun AppContent(
    settingsRepo: SettingsRepository,
    isServiceEnabled: Boolean,
    isControllerConnected: Boolean,
    controllerName: String,
    onOpenSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sensitivity by settingsRepo.sensitivityFlow.collectAsState(initial = 1.0f)
    val acceleration by settingsRepo.accelerationEnabledFlow.collectAsState(initial = true)

    val mappings by settingsRepo.buttonMappingsFlow.collectAsState(initial = mapOf())

    var showRemapDialog by remember { mutableStateOf<String?>(null) }

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
                Text("Sensitivity")
                Slider(
                    value = sensitivity,
                    onValueChange = { coroutineScope.launch { settingsRepo.updateSensitivity(it) } },
                    valueRange = 0.1f..3.0f
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
                    "TOGGLE_KEYBOARD" to "Show Keyboard",
                    "DRAG" to "Hold to Drag"
                )

                actionLabels.forEach { (actionId, label) ->
                    val currentKey = mappings.entries.find { it.value == actionId }?.key ?: -1
                    val keyName = KeyEvent.keyCodeToString(currentKey).replace("KEYCODE_", "")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        Button(onClick = { showRemapDialog = actionId }) {
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
                OutlinedTextField(value = "", onValueChange = {}, label = { Text("Type here...") }, modifier = Modifier.fillMaxWidth())
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
    if (showRemapDialog != null) {
        Dialog(onDismissRequest = { showRemapDialog = null }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Press any gamepad button", style = MaterialTheme.typography.titleLarge)
                    Text("To bind to: ${showRemapDialog}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showRemapDialog = null }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
