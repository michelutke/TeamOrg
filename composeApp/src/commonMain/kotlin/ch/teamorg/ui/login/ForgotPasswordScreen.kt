package ch.teamorg.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.components.TeamorgLoader
import ch.teamorg.ui.components.TeamorgMark
import ch.teamorg.ui.components.TeamorgTextField
import ch.teamorg.ui.testTagsAsResourceId
import ch.teamorg.ui.theme.PillShape

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onBackToLogin: () -> Unit
) {
    val state by viewModel.state.collectAsState()

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
            TeamorgMark(modifier = Modifier.size(72.dp))

            Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (state.confirmationShown) {
                Text(
                    text = "If an account exists for this email address, we have sent you a link to reset your password.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("forgot_password_confirmation")
                )
            } else {
                Text(
                    text = "Enter your email address and we will send you a link to reset your password.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                TeamorgTextField(
                    value = state.email,
                    onValueChange = { viewModel.onEmailChange(it) },
                    label = "Email",
                    modifier = Modifier.fillMaxWidth().testTag("forgot_password_email"),
                    enabled = !state.isLoading,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.onSubmitClick() },
                    modifier = Modifier.fillMaxWidth().height(57.dp).testTag("forgot_password_submit"),
                    enabled = !state.isLoading,
                    shape = PillShape
                ) {
                    if (state.isLoading) {
                        TeamorgLoader(size = 40.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Send reset link", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            TextButton(
                onClick = onBackToLogin,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("forgot_password_back")
            ) {
                Text(
                    "Back to sign in",
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
