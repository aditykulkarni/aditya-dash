package com.example.opendash.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.opendash.google.GoogleNavConfig
import com.example.opendash.google.GoogleNavEngine
import com.example.opendash.google.MapEngine
import com.example.opendash.viewmodel.DashViewModel
import com.example.opendash.voice.PlaceResolver

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Settings for the two things this fork adds on top of OpenDash: Google Maps as the
 * dash map engine (rider's own API key) and push-to-talk voice commands.
 */
@Composable
fun MapAndVoiceSettings(dashViewModel: DashViewModel) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as android.app.Application
    val config = remember { GoogleNavConfig.get(ctx) }
    val google = remember { GoogleNavEngine.get(app) }
    val dashUi by dashViewModel.ui.collectAsState()

    var engine by remember { mutableStateOf(config.engine) }
    var key by remember { mutableStateOf(config.apiKey) }
    var keySaved by remember { mutableStateOf(config.hasKey) }
    // Don't touch the Google SDK until there is a key for it to start with.
    var terms by remember { mutableStateOf(config.hasKey && google.termsAccepted) }
    var night by remember { mutableStateOf(config.nightMap) }
    val label = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text("Dash map", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = engine == MapEngine.GOOGLE,
                onClick = { engine = MapEngine.GOOGLE; dashViewModel.setMapEngine(MapEngine.GOOGLE) },
                label = { Text("Google Maps") },
            )
            FilterChip(
                selected = engine == MapEngine.OFFLINE,
                onClick = { engine = MapEngine.OFFLINE; dashViewModel.setMapEngine(MapEngine.OFFLINE) },
                label = { Text("Offline map") },
            )
        }
        Text(
            if (engine == MapEngine.GOOGLE)
                "Google's map, traffic and rerouting. Needs mobile data and your API key. " +
                    "If Google can't start, the ride falls back to the offline map."
            else
                "OpenStreetMap tiles and a route saved when you plan the trip. Keeps working " +
                    "without signal, but cannot reroute until signal returns.",
            color = label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; keySaved = false },
            label = { Text("Google API key (Navigation SDK)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                config.apiKey = key
                keySaved = config.hasKey
                terms = keySaved && google.termsAccepted
            }) { Text("Save key") }
            OutlinedButton(
                enabled = keySaved,
                onClick = {
                    ctx.findActivity()?.let { activity -> google.showTerms(activity) { terms = it } }
                },
            ) { Text(if (terms) "Terms accepted" else "Accept Google terms") }
        }
        Text(
            when {
                !keySaved -> "Step 1: paste your key and save it. It stays on this phone only."
                google.keyChangeNeedsRestart -> "Key changed. Close the app fully and reopen it to use the new key."
                !terms -> "Step 2: accept Google's navigation terms once."
                else -> "Google Maps is ready."
            },
            color = label, fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Night colours on the dash map", Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = night, onCheckedChange = {
                night = it; config.nightMap = it; google.applyNightMode()
            })
        }
        dashUi.engineNote?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

        // ── Voice commands ──────────────────────────────────────────────────
        Spacer(Modifier.height(18.dp))
        Text("Voice commands", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        var micGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }
        val micLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { micGranted = it }
        Text(
            "Press the joystick in while connected to the dash (or pick Voice command in the " +
                "dash menu), then speak through your " +
                "earbuds. Try: \"navigate to home\", \"next song\", \"pause\", \"volume up\", " +
                "\"zoom out\", \"how far\", \"mute directions\", \"stop navigation\".",
            color = label, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!micGranted) {
                Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Allow microphone")
                }
            } else {
                Button(onClick = { dashViewModel.startVoiceCommand() }, enabled = !dashUi.voiceListening) {
                    Text(if (dashUi.voiceListening) "Listening…" else "Try a command")
                }
            }
            dashUi.voiceHeard?.takeIf { it.isNotBlank() }?.let {
                Text("Heard: $it", color = label, fontSize = 12.sp)
            }
        }

        // ── Joystick, calls and dash extras ─────────────────────────────────
        Spacer(Modifier.height(18.dp))
        Text("Joystick and calls", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "UP plays or pauses music. LEFT and RIGHT skip tracks. DOWN opens a menu on " +
                "the dash with your recent calls: UP and DOWN move, RIGHT chooses, LEFT goes " +
                "back. When the phone rings, UP answers and DOWN rejects; DOWN also ends a call.",
            color = label, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
        )
        val phonePerms = arrayOf(
            Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS,
        )
        fun phoneGranted() = phonePerms.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
        var phoneOk by remember { mutableStateOf(phoneGranted()) }
        val phoneLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { phoneOk = phoneGranted() }
        if (phoneOk) {
            Text("Calls and call history are allowed.", color = label, fontSize = 12.sp)
        } else {
            Button(onClick = { phoneLauncher.launch(phonePerms) }) { Text("Allow calls and call history") }
            Text(
                "If nothing happens when you tap this, open the phone's Settings, Apps, " +
                    "Aditya Dash, Permissions and allow Phone and Call logs there.",
                color = label, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text("Shown on the dash", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        val extras = remember { com.example.opendash.dash.DashExtras.get(ctx) }
        var showMessages by remember { mutableStateOf(extras.showMessages) }
        var showMessageText by remember { mutableStateOf(extras.showMessageText) }
        var showWeather by remember { mutableStateOf(extras.showWeather) }
        var showSpeed by remember { mutableStateOf(extras.showSpeedWarning) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New messages (needs notification access)", Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = showMessages, onCheckedChange = { showMessages = it; extras.showMessages = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show the message text, not only the sender", Modifier.weight(1f), fontSize = 14.sp)
            Switch(
                checked = showMessageText, enabled = showMessages,
                onCheckedChange = { showMessageText = it; extras.showMessageText = it },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weather where you are", Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = showWeather, onCheckedChange = { showWeather = it; extras.showWeather = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed limit sign when you are over it (Google Maps only)", Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = showSpeed, onCheckedChange = { showSpeed = it; extras.showSpeedWarning = it })
        }

        // ── Saved places ────────────────────────────────────────────────────
        Spacer(Modifier.height(18.dp))
        Text("Saved places", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "Names you can say after \"navigate to\". Coordinates are latitude,longitude, " +
                "for example 18.5204,73.8567. Saved places work without signal.",
            color = label, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
        )
        var places by remember { mutableStateOf(PlaceResolver.saved(ctx)) }
        var placeName by remember { mutableStateOf("") }
        var placeLatLng by remember { mutableStateOf("") }
        var placeError by remember { mutableStateOf(false) }
        places.toSortedMap().forEach { (name, place) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$name  ·  %.4f, %.4f".format(place.lat, place.lng), Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = { PlaceResolver.remove(ctx, name); places = PlaceResolver.saved(ctx) }) {
                    Text("Remove")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = placeName, onValueChange = { placeName = it }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = placeLatLng, onValueChange = { placeLatLng = it; placeError = false },
                singleLine = true, isError = placeError,
                label = { Text("lat,lng") }, modifier = Modifier.weight(1.3f),
            )
        }
        Button(
            onClick = {
                if (PlaceResolver.save(ctx, placeName, placeLatLng)) {
                    places = PlaceResolver.saved(ctx); placeName = ""; placeLatLng = ""
                } else placeError = true
            },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Add place") }

        Text(
            "Offline map data © OpenStreetMap contributors.",
            color = label, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp),
        )
    }
}
