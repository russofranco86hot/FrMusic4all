package com.metrolist.music.ui.component

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import com.google.android.gms.cast.framework.CastContext
import timber.log.Timber

object GoogleCastBridge {
    val isAvailable = true

    fun showDialog(context: Context) {
        try {
            val castContext = CastContext.getSharedInstance(context)
            val themedContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
            val session = castContext.sessionManager.currentCastSession
            if (session != null && session.isConnected) {
                MediaRouteControllerDialog(themedContext).show()
            } else {
                val dialog = MediaRouteChooserDialog(themedContext)
                dialog.routeSelector = castContext.mergedSelector ?: androidx.mediarouter.media.MediaRouteSelector.EMPTY
                dialog.show()
            }
        } catch (e: Exception) {
            Timber.e(e, "GoogleCastBridge: failed to show dialog")
        }
    }
}
