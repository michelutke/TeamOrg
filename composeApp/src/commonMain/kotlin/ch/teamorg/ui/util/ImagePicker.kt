package ch.teamorg.ui.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberImagePickerLauncher(onResult: (bytes: ByteArray, ext: String) -> Unit): () -> Unit

/** Returns null when no camera is available (e.g. simulator/emulator without one). */
@Composable
expect fun rememberCameraCaptureLauncher(onResult: (bytes: ByteArray, ext: String) -> Unit): (() -> Unit)?
