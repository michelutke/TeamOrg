package ch.teamorg.ui.club

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.components.TeamorgTextField
import ch.teamorg.ui.testTagsAsResourceId
import ch.teamorg.ui.theme.PillShape
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubSetupScreen(
    viewModel: ClubSetupViewModel,
    onBack: () -> Unit,
    onClubCreated: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            if (event is ClubSetupEvent.ClubCreated) {
                onClubCreated(event.club.id)
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
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Set Up Your Club",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                "Tell us about your club. You can change these details later.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Logo selection
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (state.logoUrl != null) {
                    AsyncImage(
                        model = state.logoUrl,
                        contentDescription = "Club Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (state.isLogoUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                } else {
                    IconButton(
                        onClick = { /* Launch Image Picker */ },
                        modifier = Modifier.testTag("btn_add_logo")
                    ) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = "Add Logo",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            TeamorgTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Club Name",
                modifier = Modifier.fillMaxWidth().testTag("tf_club_name"),
                isError = state.error != null && state.name.isBlank()
            )

            TeamorgTextField(
                value = state.sportType,
                onValueChange = viewModel::onSportTypeChange,
                label = "Sport Type",
                modifier = Modifier.fillMaxWidth().testTag("tf_sport_type")
            )

            TeamorgTextField(
                value = state.location,
                onValueChange = viewModel::onLocationChange,
                label = "Location (Optional)",
                modifier = Modifier.fillMaxWidth().testTag("tf_location")
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
                onClick = viewModel::createClub,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(57.dp)
                    .padding(bottom = 0.dp)
                    .testTag("btn_create_club"),
                enabled = !state.isLoading && state.name.isNotBlank(),
                shape = PillShape
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Club", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
