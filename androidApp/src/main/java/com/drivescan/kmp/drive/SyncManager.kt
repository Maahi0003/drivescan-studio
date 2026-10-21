package com.drivescan.kmp.drive

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.drivescan.kmp.auth.AuthManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Smart sync (v1.1.0): wraps [DriveSync] with the user's auto-sync
 * preference. When auto sync is ON (the default) a sync is attempted
 *  - when a document changes locally ([onLocalChange]),
 *  - when network connectivity returns ([registerNetworkCallback]),
 *  - on app start ([onAppStart]).
 * When OFF, sync only ever runs from an explicit "Sync now" tap, which
 * calls [DriveSync.syncNow] directly. Syncs never overlap.
 */
object SyncManager {

    private const val PREFS = "drivescan_prefs"
    private const val KEY_AUTO_SYNC = "auto_sync"

    private val inFlight = AtomicBoolean(false)

    @Volatile
    private var callbackRegistered = false

    fun autoSyncEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SYNC, true)

    fun setAutoSync(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        if (enabled) maybeAutoSync(context)
    }

    /** Call after any local create/edit/delete of a document. */
    fun onLocalChange(context: Context) = maybeAutoSync(context)

    /** Call once from the dashboard: attempts a sync at app start. */
    fun onAppStart(context: Context) = maybeAutoSync(context)

    /** Registers (once) a callback that syncs when connectivity returns. */
    fun registerNetworkCallback(context: Context) {
        if (callbackRegistered) return
        callbackRegistered = true
        try {
            val appContext = context.applicationContext
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    maybeAutoSync(appContext)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        maybeAutoSync(appContext)
                    }
                }
            })
        } catch (e: Exception) {
            // No callback (older/limited device): app-start + local-change syncs still run.
            callbackRegistered = false
        }
    }

    fun maybeAutoSync(context: Context) {
        if (!autoSyncEnabled(context)) return
        if (!AuthManager.isConnected(context)) return
        if (!inFlight.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        thread {
            try {
                DriveSync.syncNow(appContext)
            } catch (e: Exception) {
                // Silent: the pending indicator stays up and the next
                // trigger (or a manual Sync now) retries.
            } finally {
                inFlight.set(false)
            }
        }
    }
}
