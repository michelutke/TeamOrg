package ch.teamorg.ui.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.teamorg.payments.SetupResult
import ch.teamorg.payments.rememberCardSetupSheet
import ch.teamorg.ui.components.TeamorgLoader
import ch.teamorg.ui.testTagsAsResourceId
import ch.teamorg.ui.theme.PillShape
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    viewModel: BillingViewModel,
    clubId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(clubId) {
        viewModel.load(clubId)
    }

    var pendingClientSecret by remember { mutableStateOf<String?>(null) }
    var isPresentingSheet by remember { mutableStateOf(false) }
    val presentSheet = rememberCardSetupSheet(onResult = { result ->
        isPresentingSheet = false
        when (result) {
            SetupResult.Completed -> pendingClientSecret?.let { viewModel.onCardSetupCompleted(it) }
            SetupResult.Canceled -> { /* no-op — user can retry */ }
            SetupResult.Failed -> viewModel.onCardSetupFailed()
        }
    })

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is BillingEvent.PresentCardSheet -> {
                    pendingClientSecret = event.clientSecret
                    isPresentingSheet = true
                    presentSheet(event.publishableKey, event.clientSecret)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTagsAsResourceId(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Billing",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.notOwner -> {
                    Text(
                        "Only the club owner can manage billing.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                            .testTag("txt_not_owner"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.isLoading && state.billingInfo == null -> {
                    TeamorgLoader(modifier = Modifier.align(Alignment.Center))
                }
                state.billingInfo == null && state.error != null -> {
                    Text(
                        state.error ?: "Unable to load billing",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                            .testTag("txt_billing_error"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                state.billingInfo != null -> {
                    val billingInfo = state.billingInfo!!
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            StatusChip(billingInfo.billingStatus)
                        }

                        if (billingInfo.billingMode == "stripe") {
                            item {
                                CardOnFileRow(
                                    cardBrand = billingInfo.cardBrand,
                                    cardLast4 = billingInfo.cardLast4,
                                    cardExpMonth = billingInfo.cardExpMonth,
                                    cardExpYear = billingInfo.cardExpYear
                                )
                            }
                            item {
                                Button(
                                    onClick = { viewModel.updateCard() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("btn_update_card"),
                                    enabled = !state.isUpdatingCard && !isPresentingSheet,
                                    shape = PillShape
                                ) {
                                    Text("Update card")
                                }
                            }
                        } else {
                            item {
                                Text(
                                    "Billing is managed manually for this club.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("txt_manual_billing_note")
                                )
                            }
                        }

                        item {
                            MemberCountsCard(
                                currentMemberCount = billingInfo.currentMemberCount,
                                projectedBilledCount = billingInfo.projectedBilledCount
                            )
                        }

                        item {
                            Button(
                                onClick = { viewModel.convert() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("btn_convert"),
                                enabled = !state.isConverting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = PillShape
                            ) {
                                Text(if (billingInfo.kind == "club") "Convert to team" else "Convert to club")
                            }
                        }

                        if (state.error != null) {
                            item {
                                Text(
                                    state.error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("txt_billing_error")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(billingStatus: String) {
    val (label, container, content) = when (billingStatus) {
        "active" -> Triple(
            "Active",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        "past_due" -> Triple(
            "Past due",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        "frozen" -> Triple(
            "Frozen",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            billingStatus,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("chip_billing_status")
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun CardOnFileRow(
    cardBrand: String?,
    cardLast4: String?,
    cardExpMonth: Int?,
    cardExpYear: Int?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (cardBrand != null && cardLast4 != null) {
                Text(
                    "$cardBrand •••• $cardLast4",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Exp $cardExpMonth/$cardExpYear",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "No card on file",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MemberCountsCard(
    currentMemberCount: Int,
    projectedBilledCount: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "$currentMemberCount members",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$projectedBilledCount billed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Billed count is the higher of the year-end count and the season median.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
