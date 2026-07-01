package ch.teamorg.di

import ch.teamorg.data.AttendanceCacheManager
import ch.teamorg.data.DatabaseDriverFactory
import ch.teamorg.data.EventCacheManager
import ch.teamorg.data.MutationQueueManager
import ch.teamorg.data.NotificationCacheManager
import ch.teamorg.data.createDatabase
import ch.teamorg.data.network.HttpClientFactory
import ch.teamorg.data.repository.AbwesenheitRepositoryImpl
import ch.teamorg.data.repository.AttendanceRepositoryImpl
import ch.teamorg.data.repository.AuthRepositoryImpl
import ch.teamorg.data.repository.ClubRepositoryImpl
import ch.teamorg.data.repository.EventRepositoryImpl
import ch.teamorg.data.repository.InviteRepositoryImpl
import ch.teamorg.data.repository.NotificationRepositoryImpl
import ch.teamorg.data.repository.TeamRepositoryImpl
import android.content.Context
import ch.teamorg.preferences.UserPreferences
import com.russhwolf.settings.SharedPreferencesSettings
import ch.teamorg.repository.AbwesenheitRepository
import ch.teamorg.repository.AttendanceRepository
import ch.teamorg.repository.AuthRepository
import ch.teamorg.repository.ClubRepository
import ch.teamorg.repository.EventRepository
import ch.teamorg.repository.InviteRepository
import ch.teamorg.repository.NotificationRepository
import ch.teamorg.repository.TeamRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val sharedModule = module {
    single {
        UserPreferences(
            SharedPreferencesSettings(
                get<Context>().getSharedPreferences("teamorg_prefs", Context.MODE_PRIVATE)
            )
        )
    }
    single { HttpClientFactory.create(get()) }
    single { DatabaseDriverFactory(get()) }
    single { createDatabase(get()) }
    singleOf(::EventCacheManager)
    singleOf(::AttendanceCacheManager)
    singleOf(::MutationQueueManager)
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    singleOf(::ClubRepositoryImpl) bind ClubRepository::class
    singleOf(::TeamRepositoryImpl) bind TeamRepository::class
    singleOf(::InviteRepositoryImpl) bind InviteRepository::class
    singleOf(::EventRepositoryImpl) bind EventRepository::class
    singleOf(::AttendanceRepositoryImpl) bind AttendanceRepository::class
    singleOf(::AbwesenheitRepositoryImpl) bind AbwesenheitRepository::class
    singleOf(::NotificationCacheManager)
    singleOf(::NotificationRepositoryImpl) bind NotificationRepository::class
}
