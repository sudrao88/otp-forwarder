package com.otpforwarder.ui.screen.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.RecipientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipientsViewModel @Inject constructor(
    private val recipientRepository: RecipientRepository,
    ruleRepository: ForwardingRuleRepository
) : ViewModel() {

    val recipients: StateFlow<List<RecipientWithRulesUi>> = combine(
        recipientRepository.getAllRecipients(),
        ruleRepository.getAllRulesWithDetails()
    ) { recips, rules ->
        recips.map { recipient ->
            val ruleNames = rules.filter { rule ->
                rule.actions.any { action ->
                    (action is RuleAction.ForwardSms && recipient.id in action.recipientIds) ||
                        (action is RuleAction.PlaceCall && action.recipientId == recipient.id)
                }
            }.map { it.name }
            RecipientWithRulesUi(recipient, ruleNames)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun setActive(recipient: Recipient, active: Boolean) {
        viewModelScope.launch {
            recipientRepository.updateRecipient(recipient.copy(isActive = active))
        }
    }

    data class RecipientWithRulesUi(
        val recipient: Recipient,
        val ruleNames: List<String>
    )
}
