package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.example.ui.AkritiScreen
import com.example.ui.AkritiViewModel
import com.example.ui.theme.AkritiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AkritiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AkritiTheme {
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                    viewModel.setMicPermissionGranted(micGranted)
                }

                LaunchedEffect(Unit) {
                    val hasMicPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    viewModel.setMicPermissionGranted(hasMicPermission)

                    val requiredPermissions = mutableListOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val missingPermissions = requiredPermissions.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }

                    if (missingPermissions.isNotEmpty()) {
                        permissionsLauncher.launch(missingPermissions.toTypedArray())
                    }
                }

                AkritiScreen(
                    viewModel = viewModel,
                    onRequestMicPermission = {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.CALL_PHONE
                            )
                        )
                    }
                )
            }
        }
    }
}
