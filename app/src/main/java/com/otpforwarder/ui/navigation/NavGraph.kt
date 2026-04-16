package com.otpforwarder.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Default.Home),
    Rules("rules", "Rules", Icons.AutoMirrored.Filled.Rule),
    Recipients("recipients", "Recipients", Icons.Default.People),
    Settings("settings", "Settings", Icons.Default.Settings),
}
