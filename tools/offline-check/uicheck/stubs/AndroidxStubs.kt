@file:Suppress("unused", "UNUSED_PARAMETER")

// Signature stubs mirroring the androidx APIs the app calls. They exist only so the
// app's Kotlin can be type-checked where Google Maven is unreachable; never shipped.

package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext = staticCompositionLocalOf<android.content.Context> { error("stub") }
