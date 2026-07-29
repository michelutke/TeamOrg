package ch.teamorg.payments

import androidx.compose.runtime.Composable

enum class SetupResult { Completed, Canceled, Failed }

@Composable
expect fun rememberCardSetupSheet(onResult: (SetupResult) -> Unit): (publishableKey: String, setupIntentClientSecret: String) -> Unit
