package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs every [RuleAction] of a matched rule in declaration order and collects
 * a per-action outcome.
 *
 * Actions are isolated: one action's failure never blocks the rest. The
 * returned list preserves the input order and is the source of truth for the
 * rule's log entry (status + per-action summary). The body runs on
 * [Dispatchers.Default] because both `TelecomManager.placeCall` and
 * `SmsManager.sendTextMessage` issue binder IPCs.
 */
@Singleton
class ExecuteRuleActionsUseCase @Inject constructor(
    private val forwardSms: ForwardSmsAction,
    private val setRingerLoud: SetRingerLoudAction,
    private val placeCall: PlaceCallAction
) {

    suspend operator fun invoke(
        otp: Otp,
        actions: List<RuleAction>,
        recipientsById: Map<Long, Recipient>,
        alreadySentTo: MutableSet<Long>
    ): List<ActionOutcome> = withContext(Dispatchers.Default) {
        actions.map { action ->
            when (action) {
                is RuleAction.ForwardSms -> forwardOutcome(action, otp, recipientsById, alreadySentTo)
                RuleAction.SetRingerLoud -> ringerOutcome()
                is RuleAction.PlaceCall -> callOutcome(action, recipientsById)
            }
        }
    }

    private fun forwardOutcome(
        action: RuleAction.ForwardSms,
        otp: Otp,
        recipientsById: Map<Long, Recipient>,
        alreadySentTo: MutableSet<Long>
    ): ActionOutcome {
        val r = forwardSms(otp, action, recipientsById, alreadySentTo)
        val attempted = r.sent + r.skipped
        val everyone = attempted + r.failed
        return when {
            everyone.isEmpty() ->
                ActionOutcome(action, ActionOutcome.Status.SKIPPED, "Forward skipped (no recipients)")
            r.sent.isEmpty() && r.failed.isEmpty() ->
                ActionOutcome(
                    action,
                    ActionOutcome.Status.SKIPPED,
                    "Forward skipped (already sent: ${names(r.skipped)})"
                )
            r.sent.isEmpty() -> {
                val summary = buildString {
                    append("Forward failed: ").append(names(r.failed))
                    if (r.skipped.isNotEmpty()) append(" (already sent: ").append(names(r.skipped)).append(")")
                }
                ActionOutcome(action, ActionOutcome.Status.FAILED, summary)
            }
            else -> {
                val summary = buildString {
                    append("Forwarded: ").append(names(r.sent))
                    if (r.skipped.isNotEmpty()) append(" (already sent: ").append(names(r.skipped)).append(")")
                    if (r.failed.isNotEmpty()) append(" (failed: ").append(names(r.failed)).append(")")
                }
                val status = if (r.failed.isEmpty()) ActionOutcome.Status.SUCCESS else ActionOutcome.Status.FAILED
                ActionOutcome(action, status, summary)
            }
        }
    }

    private fun ringerOutcome(): ActionOutcome {
        val r = setRingerLoud()
        val summary = when {
            r.success -> "Rang loudly"
            r.ringerChanged && r.dndWasActive && !r.bypassedDnd -> "Ring loud blocked by DND"
            r.ringerChanged -> "Rang loudly (DND not bypassed)"
            else -> "Ring loud failed"
        }
        val status = if (r.success) ActionOutcome.Status.SUCCESS else ActionOutcome.Status.FAILED
        return ActionOutcome(RuleAction.SetRingerLoud, status, summary)
    }

    private fun callOutcome(
        action: RuleAction.PlaceCall,
        recipientsById: Map<Long, Recipient>
    ): ActionOutcome {
        val recipient = recipientsById[action.recipientId]
            ?: return ActionOutcome(
                action,
                ActionOutcome.Status.FAILED,
                "Call skipped (recipient missing)"
            )
        val r = placeCall(recipient)
        val summary = if (r.success) "Called ${recipient.name}" else "Call to ${recipient.name} failed"
        val status = if (r.success) ActionOutcome.Status.SUCCESS else ActionOutcome.Status.FAILED
        return ActionOutcome(action, status, summary)
    }

    private fun names(list: List<Recipient>): String = list.joinToString(", ") { it.name }

    data class ActionOutcome(
        val action: RuleAction,
        val status: Status,
        val summary: String
    ) {
        val success: Boolean get() = status == Status.SUCCESS

        enum class Status { SUCCESS, SKIPPED, FAILED }
    }
}
