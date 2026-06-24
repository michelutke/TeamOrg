package ch.teamorg.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.components.TeamorgTextField
import ch.teamorg.ui.testTagsAsResourceId
import ch.teamorg.ui.theme.PillShape

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.registerSuccess.collect { onRegisterSuccess() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .testTagsAsResourceId()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 96.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Create Account",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Join your club in seconds.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            TeamorgTextField(
                value = state.displayName,
                onValueChange = { viewModel.onDisplayNameChange(it) },
                label = "Display Name",
                modifier = Modifier.fillMaxWidth().testTag("tf_display_name"),
                enabled = !state.isLoading
            )

            TeamorgTextField(
                value = state.email,
                onValueChange = { viewModel.onEmailChange(it) },
                label = "Email",
                modifier = Modifier.fillMaxWidth().testTag("tf_email"),
                enabled = !state.isLoading && !state.emailLocked,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            TeamorgTextField(
                value = state.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = "Password",
                modifier = Modifier.fillMaxWidth().testTag("tf_password"),
                enabled = !state.isLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("btn_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )

            TeamorgTextField(
                value = state.confirmPassword,
                onValueChange = { viewModel.onConfirmPasswordChange(it) },
                label = "Confirm Password",
                modifier = Modifier.fillMaxWidth().testTag("tf_confirm_password"),
                enabled = !state.isLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.onRegisterClick() },
                modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_create_account"),
                enabled = !state.isLoading,
                shape = PillShape
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Account", style = MaterialTheme.typography.titleMedium)
                }
            }

            TextButton(
                onClick = onNavigateToLogin,
                enabled = !state.isLoading,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("btn_navigate_login")
            ) {
                Text(
                    "Already have an account? Sign in",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        state.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Text(error)
            }
        }
    }
}
