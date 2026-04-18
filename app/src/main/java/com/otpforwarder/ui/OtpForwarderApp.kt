package com.otpforwarder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.otpforwarder.ui.navigation.Destinations
import com.otpforwarder.ui.navigation.Screen
import com.otpforwarder.ui.screen.home.HomeScreen
import com.otpforwarder.ui.screen.onboarding.PermissionOnboardingScreen
import com.otpforwarder.ui.screen.recipients.EditRecipientScreen
import com.otpforwarder.ui.screen.recipients.RecipientsScreen
import com.otpforwarder.ui.screen.rules.EditRuleScreen
import com.otpforwarder.ui.screen.rules.RulesScreen
import com.otpforwarder.ui.screen.settings.SettingsScreen
import com.otpforwarder.util.PermissionHelper

@Composable
fun OtpForwarderApp(permissionHelper: PermissionHelper) {
    var onboardingDone by remember { mutableStateOf(permissionHelper.hasAllRequired()) }
    if (!onboardingDone) {
        PermissionOnboardingScreen(
            onGranted = { onboardingDone = true }
        )
        return
    }
    MainNav()
}

@Composable
private fun MainNav() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = Screen.entries.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Rules.route) {
                RulesScreen(
                    onAddRule = { navController.navigate(Destinations.ADD_RULE) },
                    onEditRule = { id -> navController.navigate(Destinations.editRule(id)) }
                )
            }
            composable(Screen.Recipients.route) {
                RecipientsScreen(
                    onAddRecipient = { navController.navigate(Destinations.ADD_RECIPIENT) },
                    onEditRecipient = { id ->
                        navController.navigate(Destinations.editRecipient(id))
                    }
                )
            }
            composable(Screen.Settings.route) { SettingsScreen() }

            composable(
                route = Destinations.ADD_RULE_WITH_RECIPIENT_ROUTE,
                arguments = listOf(
                    navArgument(Destinations.ADD_RULE_WITH_RECIPIENT_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                EditRuleScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Destinations.EDIT_RULE_ROUTE,
                arguments = listOf(
                    navArgument(Destinations.EDIT_RULE_ARG) { type = NavType.StringType }
                )
            ) {
                EditRuleScreen(onBack = { navController.popBackStack() })
            }

            composable(Destinations.ADD_RECIPIENT) {
                EditRecipientScreen(
                    onBack = { navController.popBackStack() },
                    onAddNewRule = { recipientId ->
                        navController.navigate(Destinations.addRuleWithRecipient(recipientId)) {
                            popUpTo(Screen.Recipients.route) { inclusive = false }
                        }
                    }
                )
            }
            composable(
                route = Destinations.EDIT_RECIPIENT_ROUTE,
                arguments = listOf(
                    navArgument(Destinations.EDIT_RECIPIENT_ARG) { type = NavType.StringType }
                )
            ) {
                EditRecipientScreen(
                    onBack = { navController.popBackStack() },
                    onAddNewRule = { recipientId ->
                        navController.navigate(Destinations.addRuleWithRecipient(recipientId)) {
                            popUpTo(Screen.Recipients.route) { inclusive = false }
                        }
                    }
                )
            }
        }
    }
}
