package ch.teamorg.ui.selfserve

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ch.teamorg.data.repository.SelfServeCreated
import ch.teamorg.ui.components.TeamorgTextField
import ch.teamorg.ui.testTagsAsResourceId
import ch.teamorg.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTeamOrClubScreen(
    viewModel: CreateTeamOrClubViewModel,
    onBack: () -> Unit,
    onProceedToCard: (SelfServeCreated) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateTeamOrClubEvent.ProceedToCard -> onProceedToCard(event.created)
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTagsAsResourceId(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Team oder Verein erstellen",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KindCard(
                        title = "Team",
                        hint = "Für eine einzelne Mannschaft.",
                        selected = state.kind == "team",
                        onClick = { viewModel.onKindChange("team") },
                        modifier = Modifier.weight(1f).testTag("card_kind_team")
                    )
                    KindCard(
                        title = "Verein",
                        hint = "Für mehrere Teams unter einem Dach.",
                        selected = state.kind == "club",
                        onClick = { viewModel.onKindChange("club") },
                        modifier = Modifier.weight(1f).testTag("card_kind_club")
                    )
                }

                Text(
                    "Du kannst später jederzeit zwischen Team und Verein wechseln.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TeamorgTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = "Name",
                    modifier = Modifier.fillMaxWidth().testTag("tf_name"),
                    isError = state.error != null && state.name.isBlank()
                )

                TeamorgTextField(
                    value = state.sportType,
                    onValueChange = viewModel::onSportTypeChange,
                    label = "Sportart",
                    modifier = Modifier.fillMaxWidth().testTag("tf_sport_type")
                )

                TeamorgTextField(
                    value = state.location,
                    onValueChange = viewModel::onLocationChange,
                    label = "Ort (optional)",
                    modifier = Modifier.fillMaxWidth().testTag("tf_location")
                )

                TeamorgTextField(
                    value = state.billingEmail,
                    onValueChange = viewModel::onBillingEmailChange,
                    label = "Rechnungs-E-Mail",
                    modifier = Modifier.fillMaxWidth().testTag("tf_billing_email"),
                    isError = state.error != null && !state.billingEmail.contains("@")
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "CHF 2 pro Mitglied und Jahr, jeweils im Januar abgerechnet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (state.error != null) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = { viewModel.submit() },
                    modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_proceed_to_card"),
                    enabled = !state.isLoading,
                    shape = PillShape
                ) {
                    Text("Weiter zur Zahlung", style = MaterialTheme.typography.titleMedium)
                }
            }

            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.error
                ) { Text(error) }
            }
        }
    }
}

@Composable
private fun KindCard(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.border(
            width = if (selected) 2.dp else 0.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = MaterialTheme.shapes.large
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
