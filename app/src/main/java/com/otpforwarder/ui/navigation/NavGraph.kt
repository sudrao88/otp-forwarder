package com.otpforwarder.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level destinations shown in the bottom navigation bar. */
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

/** Secondary destinations that are pushed onto the nav stack. */
object Destinations {
    const val ADD_RULE = "rules/add"

    /** `ruleId` of `0` is treated as a new rule for reuse of the form. */
    const val EDIT_RULE = "rules/edit"
    const val EDIT_RULE_ARG = "ruleId"
    const val EDIT_RULE_ROUTE = "$EDIT_RULE/{$EDIT_RULE_ARG}"
    fun editRule(ruleId: Long) = "$EDIT_RULE/$ruleId"

    /** Optional pre-checked recipient when navigating from Recipients → Add Rule. */
    const val ADD_RULE_WITH_RECIPIENT_ARG = "recipientId"
    const val ADD_RULE_WITH_RECIPIENT_ROUTE =
        "$ADD_RULE?$ADD_RULE_WITH_RECIPIENT_ARG={$ADD_RULE_WITH_RECIPIENT_ARG}"

    fun addRuleWithRecipient(recipientId: Long) =
        "$ADD_RULE?$ADD_RULE_WITH_RECIPIENT_ARG=$recipientId"

    const val ADD_RECIPIENT = "recipients/add"

    const val EDIT_RECIPIENT = "recipients/edit"
    const val EDIT_RECIPIENT_ARG = "recipientId"
    const val EDIT_RECIPIENT_ROUTE = "$EDIT_RECIPIENT/{$EDIT_RECIPIENT_ARG}"
    fun editRecipient(recipientId: Long) = "$EDIT_RECIPIENT/$recipientId"

    const val ONBOARDING = "onboarding"
}
