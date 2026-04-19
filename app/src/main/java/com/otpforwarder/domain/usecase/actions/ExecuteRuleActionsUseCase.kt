package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs every [RuleAction] of a matched rule in declaration order and collects
 * a per-action outcome.
 *
 * Actions are isolated: one action's failure never blocks the rest. The
 * returned list preserves the input order and is the source of truth for the
 * rule's log entry (status + per-action summary).
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
    ): List<ActionOutcome> = actions.map { action ->
        when (action) {
            is RuleAction.ForwardSms -> {
                val r = forwardSms(otp, action, recipientsById, alreadySentTo)
                val handled = r.sent + r.skipped
                val everyone = handled + r.failed
                val success = r.failed.isEmpty() && everyone.isNotEmpty()
                val summary = when {
                    everyone.isEmpty() -> "Forward skipped (no recipients)"
                    handled.isEmpty() -> "Forward failed: ${r.failed.joinToString(", ") { it.name }}"
                    r.failed.isEmpty() -> "Forwarded: ${handled.joinToString(", ") { it.name }}"
                    else -> "Forwarded: ${handled.joinToString(", ") { it.name }} " +
                        "(failed: ${r.failed.joinToString(", ") { it.name }})"
                }
                ActionOutcome(action, success, summary)
            }
            RuleAction.SetRingerLoud -> {
                val r = setRingerLoud()
                val summary = when {
                    r.ringerChanged && r.bypassedDnd -> "Rang loudly"
                    r.ringerChanged -> "Rang loudly (DND not bypassed)"
                    else -> "Ring loud failed"
                }
                ActionOutcome(action, r.success, summary)
            }
            is RuleAction.PlaceCall -> {
                val recipient = recipientsById[action.recipientId]
                if (recipient == null) {
                    ActionOutcome(action, success = false, summary = "Call skipped (recipient missing)")
                } else {
                    val r = placeCall(recipient)
                    val summary = if (r.success) {
                        "Called ${recipient.name}"
                    } else {
                        "Call to ${recipient.name} failed"
                    }
                    ActionOutcome(action, r.success, summary)
                }
            }
        }
    }

    data class ActionOutcome(
        val action: RuleAction,
        val success: Boolean,
        val summary: String
    )
}
