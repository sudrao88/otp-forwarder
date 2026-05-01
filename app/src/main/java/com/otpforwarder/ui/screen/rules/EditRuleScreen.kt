package com.otpforwarder.ui.screen.rules

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.util.rememberContactPickerLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRuleScreen(
    onBack: () -> Unit,
    viewModel: EditRuleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addRecipientTargetActionUid by remember { mutableStateOf<Long?>(null) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> viewModel.refreshPermissionHints() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissionHints()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Rule" else "Add Rule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(
                            onClick = viewModel::requestDelete,
                            enabled = !state.inFlight
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete rule")
                        }
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Rule Name") },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ConditionsSection(
                conditions = state.conditions,
                onAdd = viewModel::addCondition,
                onRemove = viewModel::removeCondition,
                onToggleConnector = viewModel::toggleConnector,
                onOtpTypeChange = viewModel::setConditionOtpType,
                onPatternChange = viewModel::setConditionPattern
            )

            ActionsSection(
                actions = state.actions,
                allRecipients = state.allRecipients,
                showLoudModePermissionHint = state.showLoudModePermissionHint,
                showCallPermissionHint = state.showCallPermissionHint,
                onAdd = viewModel::addAction,
                onRemove = viewModel::removeAction,
                onToggleRecipient = viewModel::toggleActionRecipient,
                onCallRecipientChange = viewModel::setCallRecipient,
                onAddInlineRecipient = { uid -> addRecipientTargetActionUid = uid },
                onGrantLoudMode = viewModel::openNotificationPolicySettings,
                onGrantCallPhone = { callPermissionLauncher.launch(Manifest.permission.CALL_PHONE) }
            )

            OutlinedTextField(
                value = state.priority,
                onValueChange = viewModel::setPriority,
                label = { Text("Priority") },
                supportingText = { Text("Lower numbers run first when multiple rules match.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            state.generalError?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = { viewModel.save(onBack) },
                enabled = !state.inFlight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Rule")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete rule?") },
            text = { Text("This will remove the rule \"${state.name.ifBlank { "Untitled" }}\". Incoming OTPs matching it will stop forwarding.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onBack) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text("Cancel")
                }
            }
        )
    }

    addRecipientTargetActionUid?.let { actionUid ->
        InlineAddRecipientSheet(
            onDismiss = { addRecipientTargetActionUid = null },
            onAdd = { name, phone ->
                viewModel.addInlineRecipient(name, phone) { newId ->
                    when (state.actions.firstOrNull { it.actionUid == actionUid }) {
                        is ActionUi.ForwardSms -> viewModel.toggleActionRecipient(actionUid, newId)
                        is ActionUi.PlaceCall -> viewModel.setCallRecipient(actionUid, newId)
                        else -> Unit
                    }
                    addRecipientTargetActionUid = null
                }
            }
        )
    }
}

@Composable
private fun ConditionsSection(
    conditions: List<ConditionUi>,
    onAdd: (ConditionKind) -> Unit,
    onRemove: (Int) -> Unit,
    onToggleConnector: (Int) -> Unit,
    onOtpTypeChange: (Int, OtpType) -> Unit,
    onPatternChange: (Int, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "If the message…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "The message must match these conditions. Tap AND / OR between them to change how they combine.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        conditions.forEachIndexed { index, condition ->
            if (index > 0) {
                ConnectorSelector(
                    connector = condition.connector,
                    onToggle = { onToggleConnector(index) }
                )
            }
            ConditionRow(
                condition = condition,
                onRemove = { onRemove(index) },
                onOtpTypeChange = { onOtpTypeChange(index, it) },
                onPatternChange = { onPatternChange(index, it) }
            )
        }

        AddConditionButton(onAdd = onAdd)
    }
}

@Composable
private fun ConnectorSelector(connector: Connector, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        FilterChip(
            selected = connector == Connector.AND,
            onClick = { if (connector != Connector.AND) onToggle() },
            label = { Text("AND") }
        )
        FilterChip(
            selected = connector == Connector.OR,
            onClick = { if (connector != Connector.OR) onToggle() },
            label = { Text("OR") }
        )
    }
}

@Composable
private fun ConditionRow(
    condition: ConditionUi,
    onRemove: () -> Unit,
    onOtpTypeChange: (OtpType) -> Unit,
    onPatternChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (condition) {
                    is ConditionUi.OtpTypeIs -> OtpTypeConditionBody(
                        type = condition.type,
                        onChange = onOtpTypeChange
                    )
                    is ConditionUi.SenderMatches -> PatternConditionBody(
                        prefix = "is from",
                        pattern = condition.pattern,
                        placeholder = "e.g. HDFCBK",
                        helper = "Matches the SMS sender. Supports regex.",
                        error = condition.error,
                        onChange = onPatternChange
                    )
                    is ConditionUi.BodyContains -> PatternConditionBody(
                        prefix = "has",
                        pattern = condition.pattern,
                        placeholder = "e.g. credited",
                        helper = "Matches text anywhere in the SMS. Supports regex.",
                        suffix = "in the body",
                        error = condition.error,
                        onChange = onPatternChange
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove condition")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtpTypeConditionBody(type: OtpType, onChange: (OtpType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = otpTypeLabel(type),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("OTP type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            visibleOtpTypes.forEach { option ->
                DropdownMenuItem(
                    text = { Text(otpTypeLabel(option)) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternConditionBody(
    prefix: String,
    pattern: String,
    placeholder: String,
    helper: String,
    error: String?,
    onChange: (String) -> Unit,
    suffix: String? = null
) {
    Column {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = prefix,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = onChange,
                singleLine = true,
                placeholder = { Text(placeholder) },
                isError = error != null,
                modifier = Modifier.weight(1f, fill = false)
            )
            suffix?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        Text(
            text = error ?: helper,
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AddConditionButton(onAdd: (ConditionKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("+ Add condition")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("OTP type…") },
                onClick = {
                    onAdd(ConditionKind.OTP_TYPE)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Sender matches…") },
                onClick = {
                    onAdd(ConditionKind.SENDER)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Body contains…") },
                onClick = {
                    onAdd(ConditionKind.BODY)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun ActionsSection(
    actions: List<ActionUi>,
    allRecipients: List<Recipient>,
    showLoudModePermissionHint: Boolean,
    showCallPermissionHint: Boolean,
    onAdd: (ActionKind) -> Unit,
    onRemove: (Long) -> Unit,
    onToggleRecipient: (Long, Long) -> Unit,
    onCallRecipientChange: (Long, Long) -> Unit,
    onAddInlineRecipient: (Long) -> Unit,
    onGrantLoudMode: () -> Unit,
    onGrantCallPhone: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Then…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        actions.forEach { action ->
            val uid = action.actionUid
            ActionRow(
                action = action,
                allRecipients = allRecipients,
                showLoudModePermissionHint = showLoudModePermissionHint,
                showCallPermissionHint = showCallPermissionHint,
                onRemove = { onRemove(uid) },
                onToggleRecipient = { onToggleRecipient(uid, it) },
                onCallRecipientChange = { onCallRecipientChange(uid, it) },
                onAddInlineRecipient = { onAddInlineRecipient(uid) },
                onGrantLoudMode = onGrantLoudMode,
                onGrantCallPhone = onGrantCallPhone
            )
        }
        val hasForward = actions.any { it is ActionUi.ForwardSms }
        val hasLoud = actions.any { it is ActionUi.SetRingerLoud }
        AddActionButton(
            onAdd = onAdd,
            forwardDisabled = hasForward,
            loudDisabled = hasLoud
        )
    }
}

@Composable
private fun ActionRow(
    action: ActionUi,
    allRecipients: List<Recipient>,
    showLoudModePermissionHint: Boolean,
    showCallPermissionHint: Boolean,
    onRemove: () -> Unit,
    onToggleRecipient: (Long) -> Unit,
    onCallRecipientChange: (Long) -> Unit,
    onAddInlineRecipient: () -> Unit,
    onGrantLoudMode: () -> Unit,
    onGrantCallPhone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (action) {
                    is ActionUi.ForwardSms -> ForwardSmsBody(
                        action = action,
                        allRecipients = allRecipients,
                        onToggleRecipient = onToggleRecipient,
                        onAddInlineRecipient = onAddInlineRecipient
                    )
                    is ActionUi.SetRingerLoud -> SetRingerLoudBody(
                        showPermissionHint = showLoudModePermissionHint,
                        onGrant = onGrantLoudMode
                    )
                    is ActionUi.PlaceCall -> PlaceCallBody(
                        action = action,
                        allRecipients = allRecipients,
                        showPermissionHint = showCallPermissionHint,
                        onCallRecipientChange = onCallRecipientChange,
                        onAddInlineRecipient = onAddInlineRecipient,
                        onGrant = onGrantCallPhone
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove action")
            }
        }
    }
}

@Composable
private fun ForwardSmsBody(
    action: ActionUi.ForwardSms,
    allRecipients: List<Recipient>,
    onToggleRecipient: (Long) -> Unit,
    onAddInlineRecipient: () -> Unit
) {
    Column {
        Text(
            text = "Forward the text to",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        if (allRecipients.isEmpty()) {
            Text(
                text = "No recipients yet. Add one below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        allRecipients.forEach { recipient ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = recipient.id in action.recipientIds,
                    onCheckedChange = { onToggleRecipient(recipient.id) }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(recipient.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        recipient.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        TextButton(onClick = onAddInlineRecipient) {
            Text("+ Add recipient")
        }
        action.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SetRingerLoudBody(showPermissionHint: Boolean, onGrant: () -> Unit) {
    Column {
        Text(
            text = "Set the phone to loud mode",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Turns the ringer on and maxes the ring volume when this rule fires.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showPermissionHint) {
            Spacer(Modifier.height(8.dp))
            PermissionHint(
                message = "Grant Do Not Disturb access so loud mode works even when the phone is in DND.",
                onGrant = onGrant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceCallBody(
    action: ActionUi.PlaceCall,
    allRecipients: List<Recipient>,
    showPermissionHint: Boolean,
    onCallRecipientChange: (Long) -> Unit,
    onAddInlineRecipient: () -> Unit,
    onGrant: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedRecipient = allRecipients.firstOrNull { it.id == action.recipientId }
    Column {
        Text(
            text = "Place a call to",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedRecipient?.name ?: "",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = { Text("Choose a recipient") },
                isError = action.error != null,
                supportingText = { action.error?.let { Text(it) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allRecipients.forEach { recipient ->
                    DropdownMenuItem(
                        text = { Text("${recipient.name} — ${recipient.phoneNumber}") },
                        onClick = {
                            onCallRecipientChange(recipient.id)
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("+ Add recipient") },
                    onClick = {
                        expanded = false
                        onAddInlineRecipient()
                    }
                )
            }
        }
        if (showPermissionHint) {
            Spacer(Modifier.height(8.dp))
            PermissionHint(
                message = "Grant the Call phone permission so this rule can place calls in the background.",
                onGrant = onGrant
            )
        }
    }
}

@Composable
private fun PermissionHint(message: String, onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun AddActionButton(
    onAdd: (ActionKind) -> Unit,
    forwardDisabled: Boolean,
    loudDisabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("+ Add action")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Forward the text to…") },
                enabled = !forwardDisabled,
                onClick = {
                    onAdd(ActionKind.FORWARD_SMS)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Set the phone to loud mode") },
                enabled = !loudDisabled,
                onClick = {
                    onAdd(ActionKind.RINGER_LOUD)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Place a call to…") },
                onClick = {
                    onAdd(ActionKind.PLACE_CALL)
                    expanded = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineAddRecipientSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val pickContact = rememberContactPickerLauncher { picked ->
        name = picked.name
        phone = picked.phoneNumber
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "New Recipient",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(
                onClick = pickContact,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonSearch, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pick from contacts")
            }
            Text(
                text = "Or type a name and number below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onAdd(name, phone) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Save Recipient")
            }
        }
    }
}

private val visibleOtpTypes: List<OtpType> = listOf(
    OtpType.ALL,
    OtpType.TRANSACTION,
    OtpType.LOGIN,
    OtpType.PARCEL_DELIVERY,
    OtpType.REGISTRATION,
    OtpType.PASSWORD_RESET,
    OtpType.GOVERNMENT
)

internal fun otpTypeLabel(type: OtpType): String = when (type) {
    OtpType.ALL -> "Is any OTP"
    OtpType.TRANSACTION -> "Is a payment OTP"
    OtpType.LOGIN -> "Is a login OTP"
    OtpType.PARCEL_DELIVERY -> "Is a parcel delivery OTP"
    OtpType.REGISTRATION -> "Is a registration OTP"
    OtpType.PASSWORD_RESET -> "Is a password reset OTP"
    OtpType.GOVERNMENT -> "Is a government OTP"
    OtpType.UNKNOWN -> "Is any OTP"
}
