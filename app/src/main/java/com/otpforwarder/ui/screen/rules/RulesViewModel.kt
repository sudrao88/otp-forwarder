package com.otpforwarder.ui.screen.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.model.RuleCondition
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.RecipientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository,
    recipientRepository: RecipientRepository
) : ViewModel() {

    val rules: StateFlow<List<RuleListItem>> = combine(
        ruleRepository.getAllRulesWithDetails(),
        recipientRepository.getAllRecipients()
    ) { rules, recipients ->
        val byId = recipients.associateBy { it.id }
        rules.map { rule ->
            RuleListItem(rule = rule, summary = summarize(rule, byId))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun setEnabled(rule: ForwardingRule, enabled: Boolean) {
        viewModelScope.launch {
            val full = ruleRepository.getRuleWithDetailsById(rule.id) ?: return@launch
            ruleRepository.updateRule(full.copy(isEnabled = enabled))
        }
    }

    data class RuleListItem(
        val rule: ForwardingRule,
        val summary: String
    )
}

internal fun summarize(rule: ForwardingRule, recipientsById: Map<Long, Recipient>): String {
    val conditionsText = rule.conditions
        .mapIndexed { i, c -> if (i == 0) describeCondition(c) else "${c.connector.name} ${describeCondition(c)}" }
        .joinToString(" ")
    val ifPart = if (conditionsText.isBlank()) "Always" else "If $conditionsText"

    val actionsText = rule.actions
        .map { describeAction(it, recipientsById) }
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    return if (actionsText.isBlank()) ifPart else "$ifPart · $actionsText"
}

private fun describeCondition(c: RuleCondition): String = when (c) {
    is RuleCondition.OtpTypeIs -> shortOtpTypeLabel(c.type)
    is RuleCondition.SenderMatches -> "from ${c.pattern.ifBlank { "…" }}"
    is RuleCondition.BodyContains -> "body has ${c.pattern.ifBlank { "…" }}"
    is RuleCondition.ContainsMapsLink -> "has Google Maps link"
}

private fun describeAction(a: RuleAction, recipientsById: Map<Long, Recipient>): String = when (a) {
    is RuleAction.ForwardSms -> {
        val names = a.recipientIds.mapNotNull { recipientsById[it]?.name }
        if (names.isEmpty()) "Forwards" else "Forwards to ${names.joinToString(", ")}"
    }
    RuleAction.SetRingerLoud -> "Rings loudly"
    is RuleAction.PlaceCall -> {
        val name = recipientsById[a.recipientId]?.name
        if (name == null) "Places a call" else "Calls $name"
    }
}

private fun shortOtpTypeLabel(type: OtpType): String = when (type) {
    OtpType.ALL -> "any OTP"
    OtpType.TRANSACTION -> "payment OTP"
    OtpType.LOGIN -> "login OTP"
    OtpType.PARCEL_DELIVERY -> "parcel delivery OTP"
    OtpType.REGISTRATION -> "registration OTP"
    OtpType.PASSWORD_RESET -> "password reset OTP"
    OtpType.GOVERNMENT -> "government OTP"
    OtpType.UNKNOWN -> "any OTP"
}
