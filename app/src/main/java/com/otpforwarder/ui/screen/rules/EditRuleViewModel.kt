package com.otpforwarder.ui.screen.rules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.OtpType
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
class EditRuleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ruleRepository: ForwardingRuleRepository,
    private val recipientRepository: RecipientRepository
) : ViewModel() {

    private val editingRuleId: Long = savedStateHandle.get<String>(Destinations.EDIT_RULE_ARG)
        ?.toLongOrNull() ?: 0L

    private val prefilledRecipientId: Long? =
        savedStateHandle.get<String>(Destinations.ADD_RULE_WITH_RECIPIENT_ARG)?.toLongOrNull()

    private val _state = MutableStateFlow(EditRuleUiState(isEditing = editingRuleId != 0L))
    val state: StateFlow<EditRuleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val allRecipients = recipientRepository.getAllRecipientsSnapshot()
            if (editingRuleId != 0L) {
                val existing = ruleRepository.getRuleWithRecipientsById(editingRuleId)
                if (existing != null) {
                    val (rule, recipients) = existing
                    _state.update {
                        it.copy(
                            name = rule.name,
                            otpType = rule.otpType,
                            priority = rule.priority.toString(),
                            senderFilter = rule.senderFilter.orEmpty(),
                            bodyFilter = rule.bodyFilter.orEmpty(),
                            allRecipients = allRecipients,
                            selectedRecipientIds = recipients.map { r -> r.id }.toSet()
                        )
                    }
                    return@launch
                }
            }
            _state.update {
                it.copy(
                    allRecipients = allRecipients,
                    selectedRecipientIds = prefilledRecipientId
                        ?.takeIf { id -> allRecipients.any { r -> r.id == id } }
                        ?.let { id -> setOf(id) }
                        ?: emptySet()
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun setOtpType(v: OtpType) = _state.update { it.copy(otpType = v) }
    fun setPriority(v: String) = _state.update { it.copy(priority = v.filter { c -> c.isDigit() }) }
    fun setSenderFilter(v: String) = _state.update { it.copy(senderFilter = v) }
    fun setBodyFilter(v: String) = _state.update { it.copy(bodyFilter = v) }

    fun toggleRecipient(id: Long) = _state.update {
        val selected = it.selectedRecipientIds
        it.copy(
            selectedRecipientIds = if (id in selected) selected - id else selected + id
        )
    }

    fun addInlineRecipient(name: String, phone: String, onDone: () -> Unit) {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        if (trimmedName.isEmpty() || trimmedPhone.isEmpty()) return
        viewModelScope.launch {
            val newId = recipientRepository.insertRecipient(
                Recipient(name = trimmedName, phoneNumber = trimmedPhone, isActive = true)
            )
            val refreshed = recipientRepository.getAllRecipientsSnapshot()
            _state.update {
                it.copy(
                    allRecipients = refreshed,
                    selectedRecipientIds = it.selectedRecipientIds + newId
                )
            }
            onDone()
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            return
        }
        val priority = s.priority.toIntOrNull() ?: 10
        val rule = ForwardingRule(
            id = editingRuleId,
            name = s.name.trim(),
            otpType = s.otpType,
            isEnabled = true,
            priority = priority,
            senderFilter = s.senderFilter.ifBlank { null },
            bodyFilter = s.bodyFilter.ifBlank { null }
        )
        viewModelScope.launch {
            if (s.isEditing) {
                val existing = ruleRepository.getRuleById(editingRuleId)
                ruleRepository.updateRule(
                    rule.copy(isEnabled = existing?.isEnabled ?: true),
                    s.selectedRecipientIds.toList()
                )
            } else {
                ruleRepository.insertRule(rule, s.selectedRecipientIds.toList())
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (!_state.value.isEditing) return
        viewModelScope.launch {
            val existing = ruleRepository.getRuleById(editingRuleId) ?: return@launch
            ruleRepository.deleteRule(existing)
            onDone()
        }
    }

    data class EditRuleUiState(
        val isEditing: Boolean = false,
        val name: String = "",
        val nameError: String? = null,
        val otpType: OtpType = OtpType.ALL,
        val priority: String = "10",
        val senderFilter: String = "",
        val bodyFilter: String = "",
        val allRecipients: List<Recipient> = emptyList(),
        val selectedRecipientIds: Set<Long> = emptySet()
    )
}

/** Snapshot helper so we can read a one-shot list without collecting. */
private suspend fun RecipientRepository.getAllRecipientsSnapshot(): List<Recipient> =
    getAllRecipients().firstOrNull().orEmpty()
