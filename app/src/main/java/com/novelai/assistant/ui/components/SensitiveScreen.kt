package com.novelai.assistant.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Applies only while credential UI is visible, preserving screenshots of ordinary reading pages. */
@Composable
fun SensitiveScreen() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.activity()?.window
        val wasSecure = ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
        val wasFiltered = view.filterTouchesWhenObscured
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 31) window?.setHideOverlayWindows(true)
        view.filterTouchesWhenObscured = true
        onDispose {
            if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= 31) window?.setHideOverlayWindows(false)
            view.filterTouchesWhenObscured = wasFiltered
        }
    }
}
