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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class EditRuleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ruleRepository: ForwardingRuleRepository,
    private val recipientRepository: RecipientRepository,
    private val permissionHelper: PermissionHelper
) : ViewModel() {

    private val editingRuleId: Long = savedStateHandle.get<Long>(Destinations.EDIT_RULE_ARG) ?: 0L

    private val prefilledRecipientId: Long? =
        savedStateHandle.get<String>(Destinations.ADD_RULE_WITH_RECIPIENT_ARG)?.toLongOrNull()

    private val actionUidSource = AtomicLong(0L)
    private fun nextActionUid(): Long = actionUidSource.incrementAndGet()

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
                            actions = existing.actions.map { a -> toActionUi(a, nextActionUid()) },
                            allRecipients = allRecipients
                        )
                    }
                    return@launch
                }
            }
            val initialActions = buildList<ActionUi> {
                val preId = prefilledRecipientId?.takeIf { id -> allRecipients.any { it.id == id } }
                if (preId != null) {
                    add(ActionUi.ForwardSms(actionUid = nextActionUid(), recipientIds = setOf(preId)))
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

    fun setPriority(v: String) = _state.update {
        val digits = v.filter { c -> c.isDigit() }
        it.copy(priority = digits.ifBlank { DEFAULT_PRIORITY.toString() })
    }

    fun addCondition(kind: ConditionKind) = _state.update { s ->
        val new: ConditionUi = when (kind) {
            ConditionKind.OTP_TYPE -> ConditionUi.OtpTypeIs(OtpType.ALL, Connector.AND)
            ConditionKind.SENDER -> ConditionUi.SenderMatches("", Connector.AND)
            ConditionKind.BODY -> ConditionUi.BodyContains("", Connector.AND)
            ConditionKind.MAPS_LINK -> ConditionUi.ContainsMapsLink(Connector.AND)
        }
        s.copy(conditions = s.conditions + new, generalError = null)
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
            is ConditionUi.ContainsMapsLink -> c
        }
        s.copy(conditions = list, generalError = null)
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
        s.copy(
            conditions = s.conditions.toMutableList().apply { removeAt(index) },
            generalError = null
        )
    }

    fun addAction(kind: ActionKind) = _state.update { s ->
        val new: ActionUi = when (kind) {
            ActionKind.FORWARD_SMS -> ActionUi.ForwardSms(actionUid = nextActionUid(), recipientIds = emptySet())
            ActionKind.RINGER_LOUD -> ActionUi.SetRingerLoud(actionUid = nextActionUid())
            ActionKind.PLACE_CALL -> ActionUi.PlaceCall(actionUid = nextActionUid(), recipientId = null)
            ActionKind.OPEN_MAPS -> ActionUi.OpenMaps(actionUid = nextActionUid(), autoLaunch = false)
        }
        s.copy(
            actions = s.actions + new,
            showLoudModePermissionHint = s.showLoudModePermissionHint ||
                (kind == ActionKind.RINGER_LOUD && !permissionHelper.hasNotificationPolicyAccess()),
            showCallPermissionHint = s.showCallPermissionHint ||
                (kind == ActionKind.PLACE_CALL && !permissionHelper.hasCallPhone()),
            generalError = null
        )
    }

    fun toggleActionRecipient(actionUid: Long, recipientId: Long) = _state.update { s ->
        val index = s.actions.indexOfFirst { it.actionUid == actionUid }
        if (index < 0) return@update s
        val list = s.actions.toMutableList()
        val a = list[index] as? ActionUi.ForwardSms ?: return@update s
        val ids = if (recipientId in a.recipientIds) a.recipientIds - recipientId
        else a.recipientIds + recipientId
        list[index] = a.copy(recipientIds = ids, error = null)
        s.copy(actions = list)
    }

    fun setCallRecipient(actionUid: Long, recipientId: Long) = _state.update { s ->
        val index = s.actions.indexOfFirst { it.actionUid == actionUid }
        if (index < 0) return@update s
        val list = s.actions.toMutableList()
        val a = list[index] as? ActionUi.PlaceCall ?: return@update s
        list[index] = a.copy(recipientId = recipientId, error = null)
        s.copy(actions = list)
    }

    fun setActionAutoLaunch(actionUid: Long, autoLaunch: Boolean) = _state.update { s ->
        val index = s.actions.indexOfFirst { it.actionUid == actionUid }
        if (index < 0) return@update s
        val list = s.actions.toMutableList()
        val a = list[index] as? ActionUi.OpenMaps ?: return@update s
        list[index] = a.copy(autoLaunch = autoLaunch)
        s.copy(
            actions = list,
            showFullScreenIntentPermissionHint = list.any { it is ActionUi.OpenMaps && it.autoLaunch } &&
                !permissionHelper.hasFullScreenIntent()
        )
    }

    fun removeAction(actionUid: Long) = _state.update { s ->
        val index = s.actions.indexOfFirst { it.actionUid == actionUid }
        if (index < 0) return@update s
        val list = s.actions.toMutableList().apply { removeAt(index) }
        s.copy(
            actions = list,
            showLoudModePermissionHint = list.any { it is ActionUi.SetRingerLoud } &&
                !permissionHelper.hasNotificationPolicyAccess(),
            showCallPermissionHint = list.any { it is ActionUi.PlaceCall } &&
                !permissionHelper.hasCallPhone(),
            showFullScreenIntentPermissionHint = list.any { it is ActionUi.OpenMaps && it.autoLaunch } &&
                !permissionHelper.hasFullScreenIntent(),
            generalError = null
        )
    }

    fun refreshPermissionHints() = _state.update { s ->
        s.copy(
            showLoudModePermissionHint = s.actions.any { it is ActionUi.SetRingerLoud } &&
                !permissionHelper.hasNotificationPolicyAccess(),
            showCallPermissionHint = s.actions.any { it is ActionUi.PlaceCall } &&
                !permissionHelper.hasCallPhone(),
            showFullScreenIntentPermissionHint = s.actions.any { it is ActionUi.OpenMaps && it.autoLaunch } &&
                !permissionHelper.hasFullScreenIntent()
        )
    }

    fun openNotificationPolicySettings() = permissionHelper.openNotificationPolicySettings()

    fun openFullScreenIntentSettings() = permissionHelper.openFullScreenIntentSettings()

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

    fun requestDelete() = _state.update { it.copy(showDeleteConfirm = true) }
    fun dismissDelete() = _state.update { it.copy(showDeleteConfirm = false) }

    fun dismissNameDialog() = _state.update { it.copy(showNameDialog = false, nameError = null) }

    fun requestSave() {
        val s = _state.value
        if (s.inFlight) return
        val generalError: String? = when {
            s.conditions.isEmpty() -> "Add at least one condition"
            s.actions.isEmpty() -> "Add at least one action"
            else -> null
        }

        val validatedConditions = s.conditions.map(::validateCondition)
        val conditionsHaveErrors = validatedConditions.any(::conditionHasError)

        val validatedActions = s.actions.map(::validateAction)
        val actionsHaveErrors = validatedActions.any(::actionHasError)

        if (generalError != null || conditionsHaveErrors || actionsHaveErrors) {
            _state.update {
                it.copy(
                    generalError = generalError,
                    conditions = validatedConditions,
                    actions = validatedActions
                )
            }
            return
        }

        _state.update { it.copy(showNameDialog = true, nameError = null) }
    }

    fun confirmSaveWithName(name: String, onDone: () -> Unit) {
        val s = _state.value
        if (s.inFlight) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(nameError = "Name is required") }
            return
        }

        val priority = s.priority.toInt()
        val rule = ForwardingRule(
            id = editingRuleId,
            name = trimmed,
            isEnabled = true,
            priority = priority,
            conditions = s.conditions.map(::toDomainCondition),
            actions = s.actions.map(::toDomainAction)
        )
        _state.update {
            it.copy(
                inFlight = true,
                name = trimmed,
                nameError = null,
                showNameDialog = false
            )
        }
        viewModelScope.launch {
            try {
                if (s.isEditing) {
                    val existing = ruleRepository.getRuleWithDetailsById(editingRuleId)
                    ruleRepository.updateRule(rule.copy(isEnabled = existing?.isEnabled ?: true))
                } else {
                    ruleRepository.insertRule(rule)
                }
                onDone()
            } finally {
                _state.update { it.copy(inFlight = false) }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val s = _state.value
        if (!s.isEditing || s.inFlight) return
        _state.update { it.copy(inFlight = true, showDeleteConfirm = false) }
        viewModelScope.launch {
            try {
                val existing = ruleRepository.getRuleWithDetailsById(editingRuleId) ?: return@launch
                ruleRepository.deleteRule(existing)
                onDone()
            } finally {
                _state.update { it.copy(inFlight = false) }
            }
        }
    }

    private fun validateCondition(c: ConditionUi): ConditionUi = when (c) {
        is ConditionUi.OtpTypeIs -> c
        is ConditionUi.SenderMatches -> c.copy(error = validatePattern(c.pattern))
        is ConditionUi.BodyContains -> c.copy(error = validatePattern(c.pattern))
        is ConditionUi.ContainsMapsLink -> c
    }

    private fun validateAction(a: ActionUi): ActionUi = when (a) {
        is ActionUi.ForwardSms ->
            if (a.recipientIds.isEmpty()) a.copy(error = "Choose at least one recipient") else a.copy(error = null)
        is ActionUi.PlaceCall ->
            if (a.recipientId == null) a.copy(error = "Choose a recipient") else a.copy(error = null)
        is ActionUi.SetRingerLoud -> a
        is ActionUi.OpenMaps -> a
    }

    private fun validatePattern(pattern: String): String? {
        if (pattern.isBlank()) return "Pattern is required"
        return runCatching {
            val compiled = Regex(pattern)
            // Smoke test: running the regex once catches a subset of bad patterns that
            // `Regex(...)` happily compiles (e.g. unbalanced look-behinds on some JVMs).
            // Catastrophic backtracking is still possible at runtime — out of scope here.
            compiled.containsMatchIn("")
        }.fold(
            onSuccess = { null },
            onFailure = { "Invalid regex" }
        )
    }

    private fun conditionHasError(c: ConditionUi): Boolean = when (c) {
        is ConditionUi.OtpTypeIs -> false
        is ConditionUi.SenderMatches -> c.error != null
        is ConditionUi.BodyContains -> c.error != null
        is ConditionUi.ContainsMapsLink -> false
    }

    private fun actionHasError(a: ActionUi): Boolean = when (a) {
        is ActionUi.ForwardSms -> a.error != null
        is ActionUi.PlaceCall -> a.error != null
        is ActionUi.SetRingerLoud -> false
        is ActionUi.OpenMaps -> false
    }

    data class EditRuleUiState(
        val isEditing: Boolean = false,
        val name: String = "",
        val nameError: String? = null,
        val priority: String = DEFAULT_PRIORITY.toString(),
        val conditions: List<ConditionUi> = emptyList(),
        val actions: List<ActionUi> = emptyList(),
        val allRecipients: List<Recipient> = emptyList(),
        val showLoudModePermissionHint: Boolean = false,
        val showCallPermissionHint: Boolean = false,
        val showFullScreenIntentPermissionHint: Boolean = false,
        val generalError: String? = null,
        val inFlight: Boolean = false,
        val showDeleteConfirm: Boolean = false,
        val showNameDialog: Boolean = false
    ) {
        /**
         * Soft warnings shown alongside the form — they do not block save.
         * Currently: warn if `OpenMapsNavigation` is used without a `ContainsMapsLink`
         * condition, since the action would skip on every non-Maps SMS.
         */
        val softWarning: String?
            get() {
                val hasOpenMaps = actions.any { it is ActionUi.OpenMaps }
                val hasMapsCondition = conditions.any { it is ConditionUi.ContainsMapsLink }
                return if (hasOpenMaps && !hasMapsCondition) {
                    "Tip: add a \"Contains Google Maps link\" condition — without it, this action will skip on messages that have no Maps link."
                } else null
            }
    }

    companion object {
        const val DEFAULT_PRIORITY = 10
    }
}

enum class ConditionKind { OTP_TYPE, SENDER, BODY, MAPS_LINK }

enum class ActionKind { FORWARD_SMS, RINGER_LOUD, PLACE_CALL, OPEN_MAPS }

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

    data class ContainsMapsLink(
        override val connector: Connector
    ) : ConditionUi
}

sealed interface ActionUi {
    val actionUid: Long

    data class ForwardSms(
        override val actionUid: Long,
        val recipientIds: Set<Long>,
        val error: String? = null
    ) : ActionUi

    data class SetRingerLoud(
        override val actionUid: Long
    ) : ActionUi

    data class PlaceCall(
        override val actionUid: Long,
        val recipientId: Long?,
        val error: String? = null
    ) : ActionUi

    data class OpenMaps(
        override val actionUid: Long,
        val autoLaunch: Boolean
    ) : ActionUi
}

private fun withConnector(c: ConditionUi, connector: Connector): ConditionUi = when (c) {
    is ConditionUi.OtpTypeIs -> c.copy(connector = connector)
    is ConditionUi.SenderMatches -> c.copy(connector = connector)
    is ConditionUi.BodyContains -> c.copy(connector = connector)
    is ConditionUi.ContainsMapsLink -> c.copy(connector = connector)
}

private fun toConditionUi(c: RuleCondition): ConditionUi = when (c) {
    is RuleCondition.OtpTypeIs -> {
        val uiType = if (c.type == OtpType.UNKNOWN) OtpType.ALL else c.type
        ConditionUi.OtpTypeIs(uiType, c.connector)
    }
    is RuleCondition.SenderMatches -> ConditionUi.SenderMatches(c.pattern, c.connector)
    is RuleCondition.BodyContains -> ConditionUi.BodyContains(c.pattern, c.connector)
    is RuleCondition.ContainsMapsLink -> ConditionUi.ContainsMapsLink(c.connector)
}

private fun toDomainCondition(c: ConditionUi): RuleCondition = when (c) {
    is ConditionUi.OtpTypeIs -> RuleCondition.OtpTypeIs(c.type, c.connector)
    is ConditionUi.SenderMatches -> RuleCondition.SenderMatches(c.pattern, c.connector)
    is ConditionUi.BodyContains -> RuleCondition.BodyContains(c.pattern, c.connector)
    is ConditionUi.ContainsMapsLink -> RuleCondition.ContainsMapsLink(c.connector)
}

private fun toActionUi(a: RuleAction, uid: Long): ActionUi = when (a) {
    is RuleAction.ForwardSms -> ActionUi.ForwardSms(actionUid = uid, recipientIds = a.recipientIds.toSet())
    RuleAction.SetRingerLoud -> ActionUi.SetRingerLoud(actionUid = uid)
    is RuleAction.PlaceCall -> ActionUi.PlaceCall(actionUid = uid, recipientId = a.recipientId)
    is RuleAction.OpenMapsNavigation -> ActionUi.OpenMaps(actionUid = uid, autoLaunch = a.autoLaunch)
}

private fun toDomainAction(a: ActionUi): RuleAction = when (a) {
    is ActionUi.ForwardSms -> RuleAction.ForwardSms(a.recipientIds.toList())
    is ActionUi.SetRingerLoud -> RuleAction.SetRingerLoud
    is ActionUi.PlaceCall -> RuleAction.PlaceCall(requireNotNull(a.recipientId))
    is ActionUi.OpenMaps -> RuleAction.OpenMapsNavigation(autoLaunch = a.autoLaunch)
}

private suspend fun RecipientRepository.getAllRecipientsSnapshot(): List<Recipient> =
    getAllRecipients().firstOrNull().orEmpty()
