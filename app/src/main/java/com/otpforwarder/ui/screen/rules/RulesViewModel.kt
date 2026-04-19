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

    val rules: StateFlow<List<RuleWithRecipientsUi>> = combine(
        ruleRepository.getAllRulesWithDetails(),
        recipientRepository.getAllRecipients()
    ) { rules, recipients ->
        val byId = recipients.associateBy { it.id }
        rules.map { rule ->
            val recipientIds = rule.actions.filterIsInstance<RuleAction.ForwardSms>()
                .flatMap { it.recipientIds }
                .distinct()
            RuleWithRecipientsUi(
                rule = rule,
                otpType = rule.firstOtpType(),
                recipients = recipientIds.mapNotNull { byId[it] }
            )
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

    data class RuleWithRecipientsUi(
        val rule: ForwardingRule,
        val otpType: OtpType,
        val recipients: List<Recipient>
    )
}

private fun ForwardingRule.firstOtpType(): OtpType =
    conditions.filterIsInstance<RuleCondition.OtpTypeIs>().firstOrNull()?.type ?: OtpType.ALL
