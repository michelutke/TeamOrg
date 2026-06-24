package ch.teamorg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeUIViewController
import ch.teamorg.di.sharedModule
import ch.teamorg.di.uiModule
import org.koin.core.context.startKoin

private var koinInitialized = false

fun initKoin() {
    if (!koinInitialized) {
        startKoin {
            modules(sharedModule, uiModule)
        }
        koinInitialized = true
    }
}

fun MainViewController() = ComposeUIViewController {
    Box(Modifier.fillMaxSize().background(Color(0xFF090912))) {
        TeamorgApp()
    }
}

fun handleDeepLink(url: String) {
    // Supported: teamorg://invite/team/{token} and https://teamorg.ch/i/{token}
    // Both end in /{token} → take the last path segment.
    val token = url.trimEnd('/').substringAfterLast('/')
    if (token.isNotBlank()) {
        DeepLinkHandler.pendingToken.value = token
    }
}
