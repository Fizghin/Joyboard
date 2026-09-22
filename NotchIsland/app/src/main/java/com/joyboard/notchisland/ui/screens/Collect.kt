package com.joyboard.notchisland.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/** Short alias so every screen collects state the same, lifecycle-aware way. */
@Composable
fun <T> StateFlow<T>.collectAsStateLifecycle(): State<T> = collectAsStateWithLifecycle()
