package com.otpforwarder.ui.screen.recipients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.RecipientRepository
import com.otpforwarder.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditRecipientViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipientRepository: RecipientRepository,
    private val ruleRepository: ForwardingRuleRepository
) : ViewModel() {

    private val editingId: Long = savedStateHandle.get<String>(Destinations.EDIT_RECIPIENT_ARG)
        ?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(EditRecipientUiState(isEditing = editingId != 0L))
    val state: StateFlow<EditRecipientUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val rules = ruleRepository.getAllRules().firstOrNull().orEmpty()
            if (editingId != 0L) {
                val recipient = recipientRepository.getRecipientById(editingId)
                val assignedRuleIds = ruleRepository.getRuleIdsForRecipient(editingId).toSet()
                if (recipient != null) {
                    _state.update {
                        it.copy(
                            name = recipient.name,
                            phoneNumber = recipient.phoneNumber,
                            isActive = recipient.isActive,
                            allRules = rules,
                            selectedRuleIds = assignedRuleIds
                        )
                    }
                    return@launch
                }
            }
            _state.update { it.copy(allRules = rules) }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun setPhone(v: String) = _state.update { it.copy(phoneNumber = v, phoneError = null) }

    fun toggleRule(ruleId: Long) = _state.update {
        val selected = it.selectedRuleIds
        it.copy(
            selectedRuleIds = if (ruleId in selected) selected - ruleId else selected + ruleId
        )
    }

    fun save(onDone: (Long) -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            return
        }
        if (s.phoneNumber.isBlank()) {
            _state.update { it.copy(phoneError = "Phone number is required") }
            return
        }
        viewModelScope.launch {
            val id = if (s.isEditing) {
                recipientRepository.updateRecipient(
                    Recipient(
                        id = editingId,
                        name = s.name.trim(),
                        phoneNumber = s.phoneNumber.trim(),
                        isActive = s.isActive
                    )
                )
                editingId
            } else {
                recipientRepository.insertRecipient(
                    Recipient(
                        name = s.name.trim(),
                        phoneNumber = s.phoneNumber.trim(),
                        isActive = true
                    )
                )
            }
            syncRuleAssignments(id, s.selectedRuleIds)
            onDone(id)
        }
    }

    fun delete(onDone: () -> Unit) {
        if (!_state.value.isEditing) return
        viewModelScope.launch {
            val existing = recipientRepository.getRecipientById(editingId) ?: return@launch
            recipientRepository.deleteRecipient(existing)
            onDone()
        }
    }

    private suspend fun syncRuleAssignments(recipientId: Long, selectedRuleIds: Set<Long>) {
        // For each rule, updateRule requires its full recipient list. We fetch the
        // rule + its existing recipients, then add or remove this recipient and
        // write back the merged list.
        val allRules = ruleRepository.getAllRules().firstOrNull().orEmpty()
        for (rule in allRules) {
            val current = ruleRepository.getRuleWithRecipientsById(rule.id)?.second.orEmpty()
            val shouldHave = rule.id in selectedRuleIds
            val has = current.any { it.id == recipientId }
            if (shouldHave == has) continue
            val newIds = if (shouldHave) {
                current.map { it.id } + recipientId
            } else {
                current.map { it.id }.filter { it != recipientId }
            }
            ruleRepository.updateRule(rule, newIds)
        }
    }

    data class EditRecipientUiState(
        val isEditing: Boolean = false,
        val name: String = "",
        val nameError: String? = null,
        val phoneNumber: String = "",
        val phoneError: String? = null,
        val isActive: Boolean = true,
        val allRules: List<ForwardingRule> = emptyList(),
        val selectedRuleIds: Set<Long> = emptySet()
    )
}
