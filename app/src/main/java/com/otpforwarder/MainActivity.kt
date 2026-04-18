package com.otpforwarder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.otpforwarder.ui.OtpForwarderApp
import com.otpforwarder.ui.theme.OtpForwarderTheme
import com.otpforwarder.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionHelper: PermissionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OtpForwarderTheme {
                OtpForwarderApp(permissionHelper = permissionHelper)
            }
        }
    }
}
