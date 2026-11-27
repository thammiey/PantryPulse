package com.pantrypulse.app.ui.community

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pantrypulse.app.data.community.LocationProvider
import kotlinx.coroutines.delay

/**
 * Android doesn't let an app switch the phone's location on by itself, so this asks instead:
 * if location is off it shows a short prompt with an "Open settings" button, then carries on
 * automatically as soon as the person has turned location on.
 *
 * Use: `val ensureLocationOn = rememberEnsureLocationOn(provider)` then `ensureLocationOn { doThingThatNeedsLocation() }`.
 */
@Composable
fun rememberEnsureLocationOn(provider: LocationProvider): (onReady: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; pending = null },
            title = { Text("Turn on location") },
            text = { Text("PantryPulse needs your phone's location to find giveaways near you. Switch location on, then come back. It continues automatically.") },
            confirmButton = { TextButton({ showDialog = false; openLocationSettings(context) }) { Text("Open settings") } },
            dismissButton = { TextButton({ showDialog = false; pending = null }) { Text("Not now") } },
        )
    }

    // After the person has gone to settings, carry on as soon as location is switched on (waits up to 2 minutes).
    LaunchedEffect(pending, showDialog) {
        val action = pending ?: return@LaunchedEffect
        if (showDialog) return@LaunchedEffect
        repeat(120) {
            if (provider.isEnabled(context)) {
                pending = null
                action()
                return@LaunchedEffect
            }
            delay(1000)
        }
        pending = null
    }

    return { onReady ->
        if (provider.isEnabled(context)) onReady() else { pending = onReady; showDialog = true }
    }
}

fun openLocationSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
