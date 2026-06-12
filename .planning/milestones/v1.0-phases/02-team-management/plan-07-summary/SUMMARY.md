# Summary - Plan 07: Android CI + Updraft Deploy

## Completed Tasks
- Created `.github/workflows/deploy-android.yml` for automatic deployment to Updraft on push to `feat/**` and `main`.
- Updated `.github/workflows/ci.yml` to include an `android-build-check` job for PR validation.
- Configured JDK 21 (Temurin) and Gradle caching in all workflows.
- Implemented dynamic APK path discovery to ensure robust uploads.

## Manual Action Required
Miggi, you need to add the following GitHub Actions secrets to the repository to enable Updraft uploads:

1. Go to: [https://github.com/michelutke/teamorg/settings/secrets/actions](https://github.com/michelutke/teamorg/settings/secrets/actions)
2. Add `UPDRAFT_APP_ID`: The application identifier from Updraft.
3. Add `UPDRAFT_API_KEY`: The upload API key from Updraft.

The deployment workflow will fail if these secrets are not configured.

## Verification
- [x] Secrets referenced via `${{ secrets.* }}`
- [x] Workflow triggers on `feat/**` and `main`
- [x] APK path resolved dynamically
- [x] `ci.yml` includes build check
