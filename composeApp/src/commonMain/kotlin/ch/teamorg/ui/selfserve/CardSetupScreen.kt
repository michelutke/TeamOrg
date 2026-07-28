package ch.teamorg.ui.selfserve

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ch.teamorg.payments.SetupResult
import ch.teamorg.payments.rememberCardSetupSheet
import ch.teamorg.ui.components.TeamorgLoader
import ch.teamorg.ui.testTagsAsResourceId
import ch.teamorg.ui.theme.PillShape
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CardSetupScreen(
    viewModel: CardSetupViewModel,
    clubId: String,
    clientSecret: String,
    publishableKey: String,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            if (event is CardSetupEvent.Done) {
                onDone()
            }
        }
    }

    val presentSheet = rememberCardSetupSheet(onResult = { result ->
        when (result) {
            SetupResult.Completed -> viewModel.confirm(clubId, clientSecret)
            SetupResult.Canceled -> { /* no-op — user can retry */ }
            SetupResult.Failed -> viewModel.onSheetFailed()
        }
    })

    Scaffold(
        modifier = Modifier.testTagsAsResourceId(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Add a payment method",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "CHF 2 per member per year, billed each January.",
                    modifier = Modifier.padding(16.dp).testTag("txt_pricing_note"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                "No charge now — your first invoice comes in January.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("txt_no_charge_hint")
            )

            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { presentSheet(publishableKey, clientSecret) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(57.dp)
                    .testTag("btn_add_card"),
                enabled = !state.isLoading,
                shape = PillShape
            ) {
                if (state.isLoading) {
                    TeamorgLoader(size = 40.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Add card", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
