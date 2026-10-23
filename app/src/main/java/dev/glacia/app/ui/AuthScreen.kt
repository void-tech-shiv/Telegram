package dev.glacia.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.glacia.app.data.TdlibSession

@Composable
fun AuthScreen(state: TdlibSession.Auth, session: TdlibSession) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val notice by session.notice.collectAsState()
    val busy by session.authBusy.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("GLACIA", style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text("Secure Telegram sign-in", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(32.dp))
        when (state) {
            TdlibSession.Auth.SetupRequired -> {
                Text("Telegram API credentials are not configured. See README for local.properties setup.")
            }
            TdlibSession.Auth.Starting -> CircularProgressIndicator()
            TdlibSession.Auth.Phone -> {
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number (+91...)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Spacer(Modifier.height(12.dp))
                Button(enabled = !busy && phone.isNotBlank(), onClick = {
                    error = null
                    try { session.submitPhone(phone.trim()) }
                    catch (_: IllegalArgumentException) { error = "Enter a valid international phone number." }
                }) { Text("Continue") }
            }
            TdlibSession.Auth.Code -> {
                Text("Enter the code sent by Telegram (often in another Telegram app).")
                OutlinedTextField(code, { code = it }, label = { Text("Login code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Spacer(Modifier.height(12.dp))
                Button(enabled = !busy && code.isNotBlank(), onClick = {
                    session.submitCode(code.trim()); code = ""
                }) { Text("Verify") }
            }
            TdlibSession.Auth.Password -> {
                Text("Enter your Telegram two-step verification password.")
                OutlinedTextField(password, { password = it },
                    label = { Text("2FA password") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                Button(enabled = !busy && password.isNotBlank(), onClick = {
                    session.submitPassword(password); password = ""
                }) { Text("Unlock") }
            }
            TdlibSession.Auth.EmailAddress, TdlibSession.Auth.EmailCode, TdlibSession.Auth.RegistrationRequired ->
                Text("This account requires an additional Telegram authorization step. " +
                    "Email and registration workflows are not supported in this build. " +
                    "Do not share your codes or passwords with anyone.")
            is TdlibSession.Auth.Error -> {
                Text(state.reason, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("If you updated local.properties, rebuild and reinstall the app.",
                    style = MaterialTheme.typography.bodySmall)
            }
            TdlibSession.Auth.Ready -> Text("Connected")
        }
        if (busy) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        notice?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { session.dismissNotice() }) { Text("Dismiss") }
        }
        Spacer(Modifier.height(24.dp))
        Text("GLACIA is an independent client; not affiliated with Telegram.",
            style = MaterialTheme.typography.labelSmall)
    }
}
