---
plan: "02"
wave: 1
phase: 2
title: "FileStorageService abstraction + local filesystem impl"
depends_on: []
autonomous: true
files_modified:
  - server/src/main/kotlin/ch/teamorg/storage/FileStorageService.kt
  - server/src/main/kotlin/ch/teamorg/storage/LocalFileStorageService.kt
  - server/src/main/kotlin/ch/teamorg/plugins/StaticFiles.kt
  - server/src/main/kotlin/ch/teamorg/di/StorageModule.kt
requirements:
  - TM-01
  - TM-20
---

# Plan 02 — FileStorageService: Abstraction + Local Filesystem

## Goal
Clean abstraction for file uploads behind an interface. Local impl writes to `server/uploads/`. Static route serves them. Swapping to S3/R2 later = implement the interface, swap the Koin binding.

## Tasks

<task id="02-01" title="FileStorageService interface">
```kotlin
interface FileStorageService {
    /**
     * Save file bytes, return the relative path (e.g. "avatar/uuid.jpg").
     * Caller can derive the public URL as "/uploads/{path}".
     */
    suspend fun save(bytes: ByteArray, type: FileType, extension: String): String

    /** Delete a previously saved file by its relative path. No-op if not found. */
    suspend fun delete(path: String)
}

enum class FileType(val dir: String) {
    AVATAR("avatar"),
    CLUB_LOGO("logo")
}
```
</task>

<task id="02-02" title="LocalFileStorageService">
```kotlin
class LocalFileStorageService(private val baseDir: File) : FileStorageService {
    override suspend fun save(bytes: ByteArray, type: FileType, extension: String): String {
        val dir = File(baseDir, type.dir).apply { mkdirs() }
        val filename = "${UUID.randomUUID()}.$extension"
        File(dir, filename).writeBytes(bytes)
        return "${type.dir}/$filename"
    }

    override suspend fun delete(path: String) {
        File(baseDir, path).delete()
    }
}
```
Base dir defaults to `File("uploads")` relative to server working directory. Configurable via environment variable `UPLOADS_DIR`.
</task>

<task id="02-03" title="Static file serving route">
In `plugins/StaticFiles.kt`:
```kotlin
fun Application.configureStaticFiles() {
    routing {
        static("/uploads") {
            files(File(System.getenv("UPLOADS_DIR") ?: "uploads"))
        }
    }
}
```
File URLs become: `http://host/uploads/avatar/{uuid}.jpg`
</task>

<task id="02-04" title="Koin binding">
```kotlin
val StorageModule = module {
    single<FileStorageService> {
        LocalFileStorageService(File(System.getenv("UPLOADS_DIR") ?: "uploads"))
    }
}
```
Add to Koin startup modules. To swap to S3: replace this binding only.
</task>

## must_haves
- [ ] `FileStorageService` is an interface (not a class)
- [ ] `LocalFileStorageService` creates subdirs if they don't exist
- [ ] Static route serves `/uploads/*` correctly
- [ ] Koin binding uses interface type, not concrete class
