package com.otpforwarder.ui.screen.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository
) : ViewModel() {

    val rules: StateFlow<List<RuleWithRecipientsUi>> = ruleRepository
        .getAllRulesWithRecipients()
        .map { list -> list.map { (rule, recs) -> RuleWithRecipientsUi(rule, recs) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setEnabled(rule: ForwardingRule, enabled: Boolean) {
        viewModelScope.launch {
            val recipientIds = ruleRepository.getRuleWithRecipientsById(rule.id)
                ?.second
                ?.map { it.id }
                .orEmpty()
            ruleRepository.updateRule(rule.copy(isEnabled = enabled), recipientIds)
        }
    }

    data class RuleWithRecipientsUi(
        val rule: ForwardingRule,
        val recipients: List<Recipient>
    )
}
