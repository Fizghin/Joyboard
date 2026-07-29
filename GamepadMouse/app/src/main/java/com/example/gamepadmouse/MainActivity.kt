package com.example.gamepadmouse

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val titleText = TextView(this).apply {
            text = "Gamepad Mouse Service"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val infoText = TextView(this).apply {
            text = "This app requires Accessibility Services to draw the cursor and keyboard overlays.\n\n" +
                   "1. Click the button below to open Accessibility Settings.\n" +
                   "2. Find 'Gamepad Mouse' in the list (often under 'Installed services').\n" +
                   "3. Toggle it ON."
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }

        val settingsButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(titleText)
        layout.addView(infoText)
        layout.addView(settingsButton)

        setContentView(layout)
    }
}
