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
import com.otpforwarder.util.PermissionHelper
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
    private val recipientRepository: RecipientRepository,
    private val permissionHelper: PermissionHelper
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
                    _state.update {
                        it.copy(
                            name = existing.name,
                            priority = existing.priority.toString(),
                            conditions = existing.conditions.map(::toConditionUi),
                            actions = existing.actions.map(::toActionUi),
                            allRecipients = allRecipients
                        )
                    }
                    return@launch
                }
            }
            val initialActions = buildList<ActionUi> {
                val preId = prefilledRecipientId?.takeIf { id -> allRecipients.any { it.id == id } }
                if (preId != null) {
                    add(ActionUi.ForwardSms(recipientIds = setOf(preId)))
                }
            }
            _state.update {
                it.copy(
                    allRecipients = allRecipients,
                    actions = initialActions
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun setPriority(v: String) = _state.update { it.copy(priority = v.filter { c -> c.isDigit() }) }

    fun addCondition(kind: ConditionKind) = _state.update { s ->
        val connector = if (s.conditions.isEmpty()) Connector.AND else Connector.AND
        val new: ConditionUi = when (kind) {
            ConditionKind.OTP_TYPE -> ConditionUi.OtpTypeIs(OtpType.ALL, connector)
            ConditionKind.SENDER -> ConditionUi.SenderMatches("", connector)
            ConditionKind.BODY -> ConditionUi.BodyContains("", connector)
        }
        s.copy(conditions = s.conditions + new)
    }

    fun setConditionOtpType(index: Int, type: OtpType) = _state.update { s ->
        val list = s.conditions.toMutableList()
        val c = list.getOrNull(index) as? ConditionUi.OtpTypeIs ?: return@update s
        list[index] = c.copy(type = type)
        s.copy(conditions = list)
    }

    fun setConditionPattern(index: Int, pattern: String) = _state.update { s ->
        val list = s.conditions.toMutableList()
        list[index] = when (val c = list.getOrNull(index) ?: return@update s) {
            is ConditionUi.SenderMatches -> c.copy(pattern = pattern, error = null)
            is ConditionUi.BodyContains -> c.copy(pattern = pattern, error = null)
            is ConditionUi.OtpTypeIs -> c
        }
        s.copy(conditions = list)
    }

    fun toggleConnector(index: Int) = _state.update { s ->
        if (index <= 0 || index >= s.conditions.size) return@update s
        val list = s.conditions.toMutableList()
        val next = when (list[index].connector) {
            Connector.AND -> Connector.OR
            Connector.OR -> Connector.AND
        }
        list[index] = withConnector(list[index], next)
        s.copy(conditions = list)
    }

    fun removeCondition(index: Int) = _state.update { s ->
        if (index !in s.conditions.indices) return@update s
        s.copy(conditions = s.conditions.toMutableList().apply { removeAt(index) })
    }

    fun addAction(kind: ActionKind) = _state.update { s ->
        val new: ActionUi = when (kind) {
            ActionKind.FORWARD_SMS -> ActionUi.ForwardSms(emptySet())
            ActionKind.RINGER_LOUD -> ActionUi.SetRingerLoud
            ActionKind.PLACE_CALL -> ActionUi.PlaceCall(null)
        }
        s.copy(
            actions = s.actions + new,
            showLoudModePermissionHint = s.showLoudModePermissionHint ||
                (kind == ActionKind.RINGER_LOUD && !permissionHelper.hasNotificationPolicyAccess()),
            showCallPermissionHint = s.showCallPermissionHint ||
                (kind == ActionKind.PLACE_CALL && !permissionHelper.hasCallPhone())
        )
    }

    fun toggleActionRecipient(index: Int, recipientId: Long) = _state.update { s ->
        val list = s.actions.toMutableList()
        val a = list.getOrNull(index) as? ActionUi.ForwardSms ?: return@update s
        val ids = if (recipientId in a.recipientIds) a.recipientIds - recipientId
        else a.recipientIds + recipientId
        list[index] = a.copy(recipientIds = ids, error = null)
        s.copy(actions = list)
    }

    fun setCallRecipient(index: Int, recipientId: Long) = _state.update { s ->
        val list = s.actions.toMutableList()
        val a = list.getOrNull(index) as? ActionUi.PlaceCall ?: return@update s
        list[index] = a.copy(recipientId = recipientId, error = null)
        s.copy(actions = list)
    }

    fun removeAction(index: Int) = _state.update { s ->
        if (index !in s.actions.indices) return@update s
        val list = s.actions.toMutableList().apply { removeAt(index) }
        s.copy(
            actions = list,
            showLoudModePermissionHint = list.any { it is ActionUi.SetRingerLoud } &&
                !permissionHelper.hasNotificationPolicyAccess(),
            showCallPermissionHint = list.any { it is ActionUi.PlaceCall } &&
                !permissionHelper.hasCallPhone()
        )
    }

    fun refreshPermissionHints() = _state.update { s ->
        s.copy(
            showLoudModePermissionHint = s.actions.any { it is ActionUi.SetRingerLoud } &&
                !permissionHelper.hasNotificationPolicyAccess(),
            showCallPermissionHint = s.actions.any { it is ActionUi.PlaceCall } &&
                !permissionHelper.hasCallPhone()
        )
    }

    fun openNotificationPolicySettings() = permissionHelper.openNotificationPolicySettings()

    fun addInlineRecipient(name: String, phone: String, onDone: (Long) -> Unit) {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        if (trimmedName.isEmpty() || trimmedPhone.isEmpty()) return
        viewModelScope.launch {
            val newId = recipientRepository.insertRecipient(
                Recipient(name = trimmedName, phoneNumber = trimmedPhone, isActive = true)
            )
            val refreshed = recipientRepository.getAllRecipientsSnapshot()
            _state.update { it.copy(allRecipients = refreshed) }
            onDone(newId)
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        var nameError: String? = null
        var generalError: String? = null

        if (s.name.isBlank()) nameError = "Name is required"
        if (s.conditions.isEmpty()) generalError = "Add at least one condition"
        else if (s.actions.isEmpty()) generalError = "Add at least one action"

        val validatedConditions = s.conditions.map(::validateCondition)
        val conditionsHaveErrors = validatedConditions.any(::conditionHasError)

        val validatedActions = s.actions.map(::validateAction)
        val actionsHaveErrors = validatedActions.any(::actionHasError)

        if (nameError != null || generalError != null || conditionsHaveErrors || actionsHaveErrors) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    generalError = generalError,
                    conditions = validatedConditions,
                    actions = validatedActions
                )
            }
            return
        }

        val priority = s.priority.toIntOrNull() ?: 10
        val rule = ForwardingRule(
            id = editingRuleId,
            name = s.name.trim(),
            isEnabled = true,
            priority = priority,
            conditions = s.conditions.map(::toDomainCondition),
            actions = s.actions.map(::toDomainAction)
        )
        viewModelScope.launch {
            if (s.isEditing) {
                val existing = ruleRepository.getRuleWithDetailsById(editingRuleId)
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
            val existing = ruleRepository.getRuleWithDetailsById(editingRuleId) ?: return@launch
            ruleRepository.deleteRule(existing)
            onDone()
        }
    }

    private fun validateCondition(c: ConditionUi): ConditionUi = when (c) {
        is ConditionUi.OtpTypeIs -> c
        is ConditionUi.SenderMatches -> c.copy(error = validatePattern(c.pattern))
        is ConditionUi.BodyContains -> c.copy(error = validatePattern(c.pattern))
    }

    private fun validateAction(a: ActionUi): ActionUi = when (a) {
        is ActionUi.ForwardSms ->
            if (a.recipientIds.isEmpty()) a.copy(error = "Choose at least one recipient") else a.copy(error = null)
        is ActionUi.PlaceCall ->
            if (a.recipientId == null) a.copy(error = "Choose a recipient") else a.copy(error = null)
        ActionUi.SetRingerLoud -> a
    }

    private fun validatePattern(pattern: String): String? {
        if (pattern.isBlank()) return "Pattern is required"
        return runCatching { Regex(pattern) }.fold(
            onSuccess = { null },
            onFailure = { "Invalid regex" }
        )
    }

    private fun conditionHasError(c: ConditionUi): Boolean = when (c) {
        is ConditionUi.OtpTypeIs -> false
        is ConditionUi.SenderMatches -> c.error != null
        is ConditionUi.BodyContains -> c.error != null
    }

    private fun actionHasError(a: ActionUi): Boolean = when (a) {
        is ActionUi.ForwardSms -> a.error != null
        is ActionUi.PlaceCall -> a.error != null
        ActionUi.SetRingerLoud -> false
    }

    data class EditRuleUiState(
        val isEditing: Boolean = false,
        val name: String = "",
        val nameError: String? = null,
        val priority: String = "10",
        val conditions: List<ConditionUi> = emptyList(),
        val actions: List<ActionUi> = emptyList(),
        val allRecipients: List<Recipient> = emptyList(),
        val showLoudModePermissionHint: Boolean = false,
        val showCallPermissionHint: Boolean = false,
        val generalError: String? = null
    )
}

enum class ConditionKind { OTP_TYPE, SENDER, BODY }

enum class ActionKind { FORWARD_SMS, RINGER_LOUD, PLACE_CALL }

sealed interface ConditionUi {
    val connector: Connector

    data class OtpTypeIs(
        val type: OtpType,
        override val connector: Connector
    ) : ConditionUi

    data class SenderMatches(
        val pattern: String,
        override val connector: Connector,
        val error: String? = null
    ) : ConditionUi

    data class BodyContains(
        val pattern: String,
        override val connector: Connector,
        val error: String? = null
    ) : ConditionUi
}

sealed interface ActionUi {
    data class ForwardSms(
        val recipientIds: Set<Long>,
        val error: String? = null
    ) : ActionUi

    data object SetRingerLoud : ActionUi

    data class PlaceCall(
        val recipientId: Long?,
        val error: String? = null
    ) : ActionUi
}

private fun withConnector(c: ConditionUi, connector: Connector): ConditionUi = when (c) {
    is ConditionUi.OtpTypeIs -> c.copy(connector = connector)
    is ConditionUi.SenderMatches -> c.copy(connector = connector)
    is ConditionUi.BodyContains -> c.copy(connector = connector)
}

private fun toConditionUi(c: RuleCondition): ConditionUi = when (c) {
    is RuleCondition.OtpTypeIs -> ConditionUi.OtpTypeIs(c.type, c.connector)
    is RuleCondition.SenderMatches -> ConditionUi.SenderMatches(c.pattern, c.connector)
    is RuleCondition.BodyContains -> ConditionUi.BodyContains(c.pattern, c.connector)
}

private fun toDomainCondition(c: ConditionUi): RuleCondition = when (c) {
    is ConditionUi.OtpTypeIs -> RuleCondition.OtpTypeIs(c.type, c.connector)
    is ConditionUi.SenderMatches -> RuleCondition.SenderMatches(c.pattern, c.connector)
    is ConditionUi.BodyContains -> RuleCondition.BodyContains(c.pattern, c.connector)
}

private fun toActionUi(a: RuleAction): ActionUi = when (a) {
    is RuleAction.ForwardSms -> ActionUi.ForwardSms(a.recipientIds.toSet())
    RuleAction.SetRingerLoud -> ActionUi.SetRingerLoud
    is RuleAction.PlaceCall -> ActionUi.PlaceCall(a.recipientId)
}

private fun toDomainAction(a: ActionUi): RuleAction = when (a) {
    is ActionUi.ForwardSms -> RuleAction.ForwardSms(a.recipientIds.toList())
    ActionUi.SetRingerLoud -> RuleAction.SetRingerLoud
    is ActionUi.PlaceCall -> RuleAction.PlaceCall(requireNotNull(a.recipientId))
}

private suspend fun RecipientRepository.getAllRecipientsSnapshot(): List<Recipient> =
    getAllRecipients().firstOrNull().orEmpty()
