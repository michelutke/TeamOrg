package ch.teamorg.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ch.teamorg.navigation.Screen
import ch.teamorg.ui.theme.PillShape

// Zenith-style floating bottom toolbar: primaryContainer pill, selected item
// expands to filled primary pill with icon + label, unselected icon only.
@Composable
fun TeamorgBottomBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    unreadCount: Long = 0
) {
    Row(
        modifier = Modifier
            .padding(bottom = 36.dp)
            .shadow(elevation = 12.dp, shape = PillShape)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingNavItem(Screen.Events, "Events", Icons.Default.Event, currentRoute == Screen.Events.route, onNavigate)
        FloatingNavItem(Screen.Calendar, "Calendar", Icons.Default.CalendarMonth, currentRoute == Screen.Calendar.route, onNavigate)
        FloatingNavItem(Screen.Teams, "Teams", Icons.Default.Groups, currentRoute == Screen.Teams.route, onNavigate)
        FloatingNavItem(
            Screen.Inbox, "Inbox", Icons.Default.Inbox, currentRoute == Screen.Inbox.route, onNavigate,
            badgeCount = unreadCount
        )
        FloatingNavItem(Screen.Profile, "Profile", Icons.Default.Person, currentRoute == Screen.Profile.route, onNavigate)
    }
}

@Composable
private fun FloatingNavItem(
    screen: Screen,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onNavigate: (Screen) -> Unit,
    badgeCount: Long = 0
) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
            .clickable { onNavigate(screen) }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
                }
            }
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = spring(dampingRatio = 0.6f)),
            exit = shrinkHorizontally()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, color = tint)
            }
        }
    }
}
