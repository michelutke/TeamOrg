package ch.teamorg.di

import ch.teamorg.auth.AuthViewModel
import ch.teamorg.ui.club.ClubMembersViewModel
import ch.teamorg.ui.club.ClubSetupViewModel
import ch.teamorg.ui.events.CreateEditEventViewModel
import ch.teamorg.ui.events.EventDetailViewModel
import ch.teamorg.ui.events.EventListViewModel
import ch.teamorg.ui.invite.InviteViewModel
import ch.teamorg.ui.inbox.InboxViewModel
import ch.teamorg.ui.inbox.NotificationSettingsViewModel
import ch.teamorg.ui.team.PlayerProfileViewModel
import ch.teamorg.ui.team.TeamRosterViewModel
import ch.teamorg.ui.team.TeamsListViewModel
import ch.teamorg.ui.emptystate.EmptyStateViewModel
import ch.teamorg.ui.login.LoginViewModel
import ch.teamorg.ui.register.RegisterViewModel
import org.koin.dsl.module

val uiModule = module {
    factory { AuthViewModel(get()) }
    factory { LoginViewModel(get()) }
    factory { RegisterViewModel(get()) }
    factory { EmptyStateViewModel(get()) }
    factory { ClubSetupViewModel(get()) }
    factory { ClubMembersViewModel(get(), get()) }
    factory { TeamRosterViewModel(get(), get()) }
    factory { TeamsListViewModel(get(), get()) }
    factory { PlayerProfileViewModel(get(), get(), get(), get(), get()) }
    factory { InviteViewModel(get(), get()) }
    factory { EventListViewModel(get(), get(), get(), get()) }
    factory { EventDetailViewModel(get(), get(), get(), get(), get()) }
    factory { CreateEditEventViewModel(get(), get(), get()) }
    factory { InboxViewModel(get()) }
    factory { NotificationSettingsViewModel(get(), get(), get()) }
}
