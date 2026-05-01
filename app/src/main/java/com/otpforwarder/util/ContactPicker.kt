package com.otpforwarder.util

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Result of a contact pick. Either field may be blank if the source row didn't
 * carry it, so callers should still validate before saving.
 */
data class PickedContact(val name: String, val phoneNumber: String)

/**
 * Launches the system contact picker scoped to phone numbers. Uses
 * [Intent.ACTION_PICK] with the Phone CONTENT_URI so the picker returns a
 * single phone row directly — no READ_CONTACTS permission required, since the
 * system grants the calling app temporary read access on the returned URI.
 */
@Composable
fun rememberContactPickerLauncher(
    onPicked: (PickedContact) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PickPhoneContract) { uri ->
        val picked = uri?.let { readContact(context.contentResolver, it) } ?: return@rememberLauncherForActivityResult
        onPicked(picked)
    }
    return remember(launcher) { { launcher.launch(Unit) } }
}

private object PickPhoneContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}

private fun readContact(resolver: ContentResolver, uri: Uri): PickedContact? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
    )
    return resolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val number = cursor.getString(0).orEmpty()
        val name = cursor.getString(1).orEmpty()
        if (number.isBlank()) null else PickedContact(name = name, phoneNumber = number)
    }
}
