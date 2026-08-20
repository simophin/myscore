package dev.fanchao.myscore.ui

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun LibraryChangeEffect(uri: Uri?, onChanged: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(uri, lifecycleOwner) {
        if (uri == null) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChanged()
        }
        context.contentResolver.registerContentObserver(uri, true, observer)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onChanged()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
}
