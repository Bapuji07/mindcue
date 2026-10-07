package com.secondmemory.android.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.ui.components.InlineMessage
import com.secondmemory.android.ui.theme.BrandGradient
import com.secondmemory.android.ui.theme.CardBorder
import com.secondmemory.android.ui.theme.CardSurface
import com.secondmemory.android.ui.theme.MindCueTheme
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.ScreenBackground

private const val MIN_PASSWORD_LENGTH = 8

@Composable
internal fun LoginScreen(
    loggingIn: Boolean,
    error: String?,
    notice: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val autofill = LocalAutofillManager.current
    val focus = LocalFocusManager.current
    // This screen leaves right after a successful sign-in; that is the moment to let the password
    // manager offer to save the credentials (a failed attempt never gets here).
    val submitting by rememberUpdatedState(loggingIn)
    DisposableEffect(autofill) {
        onDispose { if (submitting) autofill?.commit() }
    }
    val canSubmit = !loggingIn && username.isNotBlank() &&
        (if (registerMode) password.length >= MIN_PASSWORD_LENGTH else password.isNotBlank())
    val submit = {
        if (canSubmit) {
            focus.clearFocus()
            if (registerMode) onRegister(username, password) else onLogin(username, password)
        }
    }

    Scaffold(containerColor = ScreenBackground) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(72.dp).background(BrandGradient, CircleShape), contentAlignment = Alignment.Center) {
                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (registerMode) R.string.login_subtitle_register else R.string.login_subtitle_sign_in),
                    color = Muted, textAlign = TextAlign.Center
                )
                notice?.let {
                    Spacer(Modifier.height(16.dp))
                    InlineMessage(it, isError = false)
                }
                Spacer(Modifier.height(28.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(22.dp)) {
                        OutlinedTextField(
                            value = username, onValueChange = { username = it },
                            label = { Text(stringResource(R.string.field_username)) },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Text, imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentType = if (registerMode) ContentType.NewUsername else ContentType.Username
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text(stringResource(R.string.field_password)) },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = stringResource(
                                            if (passwordVisible) R.string.hide_password else R.string.show_password
                                        )
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            supportingText = if (registerMode) {
                                { Text(stringResource(R.string.password_requirement)) }
                            } else null,
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentType = if (registerMode) ContentType.NewPassword else ContentType.Password
                            }
                        )
                        AnimatedVisibility(error != null) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                error?.let { InlineMessage(it) }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = submit, enabled = canSubmit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            if (loggingIn) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                            } else {
                                Text(
                                    stringResource(if (registerMode) R.string.action_create_account else R.string.action_sign_in),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { registerMode = !registerMode }) {
                    Text(
                        stringResource(if (registerMode) R.string.login_switch_to_sign_in else R.string.login_switch_to_register),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenPreview() {
    MindCueTheme(darkTheme = isSystemInDarkTheme()) {
        LoginScreen(loggingIn = false, error = null, notice = null, onLogin = { _, _ -> }, onRegister = { _, _ -> })
    }
}
