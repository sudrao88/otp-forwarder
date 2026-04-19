package com.otpforwarder.ui.screen.rules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.model.RuleCondition
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

/**
 * Phase 1 transitional UI: still presents the old single-OtpType + sender + body
 * editor, but persists through the new condition/action data model. Phase 5 will
 * replace this with the multi-condition / multi-action UI.
 */
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
                val existing = ruleRepository.getRuleWithDetailsById(editingRuleId)
                if (existing != null) {
                    val (otpType, sender, body) = unpackConditions(existing.conditions)
                    val selected = existing.actions.filterIsInstance<RuleAction.ForwardSms>()
                        .flatMap { it.recipientIds }
                        .toSet()
                    _state.update {
                        it.copy(
                            name = existing.name,
                            otpType = otpType,
                            priority = existing.priority.toString(),
                            senderFilter = sender.orEmpty(),
                            bodyFilter = body.orEmpty(),
                            allRecipients = allRecipients,
                            selectedRecipientIds = selected
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
    fun setSenderFilter(v: String) = _state.update { it.copy(senderFilter = v, senderFilterError = null) }
    fun setBodyFilter(v: String) = _state.update { it.copy(bodyFilter = v, bodyFilterError = null) }

    private fun validateRegex(pattern: String): String? {
        if (pattern.isBlank()) return null
        return runCatching { Regex(pattern) }.fold(
            onSuccess = { null },
            onFailure = { "Invalid regex" }
        )
    }

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
        val senderFilterError = validateRegex(s.senderFilter)
        val bodyFilterError = validateRegex(s.bodyFilter)
        if (senderFilterError != null || bodyFilterError != null) {
            _state.update {
                it.copy(
                    senderFilterError = senderFilterError,
                    bodyFilterError = bodyFilterError
                )
            }
            return
        }
        val priority = s.priority.toIntOrNull() ?: 10
        val conditions = buildConditions(s.otpType, s.senderFilter, s.bodyFilter)
        val actions = listOf(RuleAction.ForwardSms(s.selectedRecipientIds.toList()))
        val rule = ForwardingRule(
            id = editingRuleId,
            name = s.name.trim(),
            isEnabled = true,
            priority = priority,
            conditions = conditions,
            actions = actions
        )
        viewModelScope.launch {
            if (s.isEditing) {
                val existing = ruleRepository.getRuleById(editingRuleId)
                ruleRepository.updateRule(rule.copy(isEnabled = existing?.isEnabled ?: true))
            } else {
                ruleRepository.insertRule(rule)
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
        val senderFilterError: String? = null,
        val bodyFilter: String = "",
        val bodyFilterError: String? = null,
        val allRecipients: List<Recipient> = emptyList(),
        val selectedRecipientIds: Set<Long> = emptySet()
    )
}

/**
 * Reduces a stored condition list back into the legacy (otpType, sender, body)
 * triple the Phase 1 editor still operates on. Picks the first matching
 * condition of each kind; everything else is dropped on save.
 */
private fun unpackConditions(
    conditions: List<RuleCondition>
): Triple<OtpType, String?, String?> {
    val otp = conditions.filterIsInstance<RuleCondition.OtpTypeIs>()
        .firstOrNull()?.type ?: OtpType.ALL
    val sender = conditions.filterIsInstance<RuleCondition.SenderMatches>()
        .firstOrNull()?.pattern
    val body = conditions.filterIsInstance<RuleCondition.BodyContains>()
        .firstOrNull()?.pattern
    return Triple(otp, sender, body)
}

private fun buildConditions(
    otpType: OtpType,
    sender: String,
    body: String
): List<RuleCondition> = buildList {
    add(RuleCondition.OtpTypeIs(otpType, Connector.AND))
    if (sender.isNotBlank()) add(RuleCondition.SenderMatches(sender, Connector.AND))
    if (body.isNotBlank()) add(RuleCondition.BodyContains(body, Connector.AND))
}

private suspend fun RecipientRepository.getAllRecipientsSnapshot(): List<Recipient> =
    getAllRecipients().firstOrNull().orEmpty()
