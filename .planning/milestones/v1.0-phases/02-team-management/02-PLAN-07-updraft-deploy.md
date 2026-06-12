---
plan: "07"
wave: 5
phase: 2
title: "CI/CD — Android build + Updraft distribution"
depends_on: ["01"]
autonomous: true
files_modified:
  - .github/workflows/ci.yml
  - .github/workflows/deploy-android.yml
requirements: []
---

# Plan 07 — Android Build + Updraft Deployment

## Goal
Every push to a feature branch builds a debug APK and uploads it to Updraft for testing.
Releases (merged to main) build a release APK and upload to Updraft separately.

## Context
- Updraft upload endpoint: `PUT https://app.getupdraft.com/api/app_upload/{APP_ID}/{API_KEY}/`
- Credentials stored as GitHub Actions secrets:
  - `UPDRAFT_APP_ID` — app identifier
  - `UPDRAFT_API_KEY` — upload API key
- **Never hardcode credentials in workflow files**
- Java is required for Gradle — use `actions/setup-java@v4` with JDK 21

## Tasks

<task id="07-01" title="Add GitHub Actions secrets (manual step — Miggi)">
Go to https://github.com/michelutke/teamorg/settings/secrets/actions and add:
- `UPDRAFT_APP_ID` = the app ID
- `UPDRAFT_API_KEY` = the API key

This is required before the workflow can upload. The workflow will fail gracefully if secrets are missing.
</task>

<task id="07-02" title="deploy-android.yml — reusable build + upload workflow">
Create `.github/workflows/deploy-android.yml`:

```yaml
name: Deploy Android to Updraft

on:
  push:
    branches:
      - 'feat/**'
      - main
  workflow_dispatch:

jobs:
  build-and-deploy:
    name: Build APK + Upload to Updraft
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Build Debug APK
        run: ./gradlew :androidApp:assembleDebug
        env:
          API_BASE_URL: ${{ vars.API_BASE_URL || 'https://api.teamorg.app' }}

      - name: Upload to Updraft
        run: |
          APK_PATH=$(find androidApp/build/outputs/apk/debug -name "*.apk" | head -1)
          echo "Uploading: $APK_PATH"
          curl -X PUT --http1.1 \
            -F "app=@$APK_PATH" \
            "https://app.getupdraft.com/api/app_upload/${{ secrets.UPDRAFT_APP_ID }}/${{ secrets.UPDRAFT_API_KEY }}/"
```
</task>

<task id="07-03" title="Update ci.yml — add build check to PR validation">
Add an Android build check to the existing `ci.yml` so PRs show a build status:

```yaml
  android-build-check:
    name: Android Build Check
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v3
      - name: Assemble Debug
        run: ./gradlew :androidApp:assembleDebug
```
</task>

## Notes
- iOS build omitted for now (requires macOS runner — higher CI cost, add when needed)
- `workflow_dispatch` allows manual trigger from GitHub UI
- APK path discovery uses `find` to handle version-stamped filenames

## must_haves
- [ ] Secrets referenced via `${{ secrets.* }}` — never hardcoded
- [ ] Workflow triggers on feat/** branches AND main
- [ ] APK path resolved dynamically (not hardcoded)
- [ ] ci.yml includes Android build check for PRs
