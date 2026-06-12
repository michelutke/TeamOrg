---
phase: 02-team-management
plan: 13
subsystem: avatar-upload
tags: [avatar, file-upload, multipart, image-picker, kmp]
dependency_graph:
  requires: []
  provides: [avatar-upload-endpoint, shared-upload-avatar, player-profile-image-picker]
  affects: [PlayerProfileScreen, AuthRoutes, TeamRepository]
tech_stack:
  added: [expect/actual rememberImagePickerLauncher (Android GetContent, iOS UIImagePickerController)]
  patterns: [multipart form upload, expect/actual Composable, Ktor submitFormWithBinaryData]
key_files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/util/ImagePicker.kt
    - composeApp/src/androidMain/kotlin/ch/teamorg/ui/util/ImagePicker.android.kt
    - composeApp/src/iosMain/kotlin/ch/teamorg/ui/util/ImagePicker.ios.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt
    - shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt
decisions:
  - "uploadAvatar placed in TeamRepository (not a new repo) — user-scoped but TeamRepo already has getMyRoles() pattern"
  - "expect/actual rememberImagePickerLauncher: Android uses ActivityResultContracts.GetContent, iOS uses UIImagePickerController — avoids adding peekaboo or mpfilepicker dependency"
  - "iOS actual converts UIImage to JPEG at 0.85 quality — simpler than passing NSData extension through PHPicker"
metrics:
  duration: "~15 minutes"
  completed: "2026-03-19"
  tasks_completed: 2
  files_changed: 10
---

# Phase 02 Plan 13: Avatar Upload Summary

Avatar upload end-to-end: multipart POST /auth/me/avatar on server, Ktor submitFormWithBinaryData in shared repo, expect/actual image picker in Compose Multiplatform — tap avatar circle on own profile to pick and upload a photo.

## Tasks Completed

| # | Task | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Server POST /me/avatar + UserRepository.updateAvatarUrl | 9d8f5a7 | AuthRoutes.kt, UserRepository.kt |
| 2 | Shared uploadAvatar + PlayerProfileScreen image picker | f523df1 | TeamRepository.kt, TeamRepositoryImpl.kt, PlayerProfileViewModel.kt, PlayerProfileScreen.kt, ImagePicker.kt (3 platforms) |

## What Was Built

### Task 1 — Server

- `UserRepository` interface: added `fun updateAvatarUrl(userId: UUID, avatarUrl: String?): User`
- `UserRepositoryImpl`: implements with Exposed `UPDATE` + `SELECT` in same transaction
- `AuthRoutes.kt`: `POST /me/avatar` inside `authenticate("jwt")` block — receives multipart, validates content-type (jpg/png/webp), enforces 2MB limit, saves via `FileStorageService.save(bytes, FileType.AVATAR, ext)`, updates `users.avatar_url` to `/uploads/{path}`, responds with updated `User` JSON
- Injected `FileStorageService` via Koin in `authRoutes()`

### Task 2 — Shared + UI

- `TeamRepository` interface: added `suspend fun uploadAvatar(imageBytes: ByteArray, extension: String): Result<Unit>`
- `TeamRepositoryImpl`: `submitFormWithBinaryData` to `/auth/me/avatar` following `ClubRepositoryImpl.uploadLogo` pattern
- `PlayerProfileViewModel.uploadAvatar`: sets loading, calls repo, reloads profile on success
- `ImagePicker.kt` (commonMain): `@Composable expect fun rememberImagePickerLauncher(...): () -> Unit`
- `ImagePicker.android.kt`: `rememberLauncherForActivityResult(GetContent)` with `image/*`, reads bytes from `ContentResolver`, resolves extension from MIME type
- `ImagePicker.ios.kt`: `UIImagePickerController` via `UIApplication.sharedApplication.keyWindow.rootViewController`, converts to JPEG at 0.85 quality
- `PlayerProfileScreen`: avatar `Box` is clickable when `state.isOwnProfile`, camera icon overlay (`Icons.Default.CameraAlt`) at bottom-right of avatar circle

## Deviations from Plan

None — plan executed exactly as written. The iOS actual uses `UIImagePickerController` (as suggested in plan option) rather than `PHPickerViewController` — simpler and avoids async NSItemProvider complexity.

## Self-Check: PASSED

Files exist:
- server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt — contains `post("/me/avatar")`
- server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt — contains `fun updateAvatarUrl`
- shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt — contains `suspend fun uploadAvatar`
- shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt — contains `/auth/me/avatar`
- composeApp/src/commonMain/kotlin/ch/teamorg/ui/util/ImagePicker.kt
- composeApp/src/androidMain/kotlin/ch/teamorg/ui/util/ImagePicker.android.kt
- composeApp/src/iosMain/kotlin/ch/teamorg/ui/util/ImagePicker.ios.kt
- composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt — contains `fun uploadAvatar`
- composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt — contains `uploadAvatar`

Commits verified: 9d8f5a7, f523df1 in git log.
Both `:server:compileKotlin` and `:composeApp:compileDebugKotlinAndroid` passed.
