package ch.teamorg.payments

import androidx.compose.runtime.Composable

private var setupPresenter: ((String, String, (String) -> Unit) -> Unit)? = null

fun setSetupPresenter(presenter: (String, String, (String) -> Unit) -> Unit) {
    setupPresenter = presenter
}

@Composable
actual fun rememberCardSetupSheet(onResult: (SetupResult) -> Unit): (publishableKey: String, setupIntentClientSecret: String) -> Unit = { pk, secret ->
    setupPresenter?.invoke(pk, secret) { code ->
        onResult(when (code) {
            "completed" -> SetupResult.Completed
            "canceled" -> SetupResult.Canceled
            else -> SetupResult.Failed
        })
    } ?: onResult(SetupResult.Failed)
}
