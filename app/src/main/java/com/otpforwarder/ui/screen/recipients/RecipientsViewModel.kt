package com.otpforwarder.ui.screen.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.domain.model.Recipient
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
    private val ruleRepository: ForwardingRuleRepository
) : ViewModel() {

    val recipients: StateFlow<List<RecipientWithRulesUi>> = combine(
        recipientRepository.getAllRecipients(),
        ruleRepository.getAllRulesWithRecipients()
    ) { recips, rulesWithRecs ->
        recips.map { recipient ->
            val ruleNames = rulesWithRecs
                .filter { (_, rs) -> rs.any { it.id == recipient.id } }
                .map { (r, _) -> r.name }
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
