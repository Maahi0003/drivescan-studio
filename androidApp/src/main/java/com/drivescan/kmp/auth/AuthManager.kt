package com.drivescan.kmp.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.drivescan.shared.DriveApi
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Google OAuth 2.0 + PKCE for the native app.
 *
 * Flow: [startAuth] opens the system browser at Google's consent page;
 * Google redirects to com.drivescan.kmp:/oauth2redirect, which lands in
 * AuthActivity; the code is exchanged for tokens there. Tokens live in
 * app-private SharedPreferences. A built-in default client ID
 * ([DEFAULT_CLIENT_ID] — the owner's Android OAuth client) ships with the
 * app so Connect works out of the box; a user can still paste their own
 * (free) Google Cloud OAuth client ID in Settings to override it.
 */
object AuthManager {
    private const val DRIVE_PREFS = "drivescan_drive"
    private const val APP_PREFS = "drivescan_prefs"

    /** Built-in Android OAuth client ID, used until the user saves their own in Settings. */
    const val DEFAULT_CLIENT_ID =
        "101242821085-v1n3n479cjl32t0isfqvqiv3oaj3ck4h.apps.googleusercontent.com"

    /** Effective client ID: the user's saved value when present, else [DEFAULT_CLIENT_ID]. */
    fun clientId(context: Context): String {
        val saved = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .getString("drive_client_id", "")?.trim() ?: ""
        return saved.ifEmpty { DEFAULT_CLIENT_ID }
    }

    fun setClientId(context: Context, id: String) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit().putString("drive_client_id", id.trim()).apply()
    }

    fun isConnected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)
        return prefs.getString("refresh_token", null) != null ||
            prefs.getString("access_token", null) != null
    }

    fun accessToken(context: Context): String? =
        context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)
            .getString("access_token", null)

    fun refreshToken(context: Context): String? =
        context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)
            .getString("refresh_token", null)

    fun tokenExpiryMs(context: Context): Long =
        context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)
            .getLong("expires_at", 0L)

    fun saveTokens(context: Context, access: String, refresh: String?, expiresInSec: Long) {
        val edit = context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("access_token", access)
            .putLong("expires_at", System.currentTimeMillis() + expiresInSec * 1000)
        if (refresh != null) edit.putString("refresh_token", refresh)
        edit.apply()
    }

    fun disconnect(context: Context) {
        context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Opens the browser consent flow. Returns false if no client ID is configured. */
    fun startAuth(activity: Activity): Boolean {
        val cid = clientId(activity)
        if (cid.isEmpty()) return false

        val verifier = newCodeVerifier()
        val state = newCodeVerifier().substring(0, 24)
        activity.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("code_verifier", verifier)
            .putString("oauth_state", state)
            .apply()

        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val url = Uri.parse(DriveApi.OAUTH_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", cid)
            .appendQueryParameter("redirect_uri", DriveApi.REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", DriveApi.SCOPE_DRIVE_FILE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
        activity.startActivity(Intent(Intent.ACTION_VIEW, url))
        return true
    }

    fun pendingVerifier(context: Context): String? =
        context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)
            .getString("code_verifier", null)

    fun pendingState(context: Context): String? =
        context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)
            .getString("oauth_state", null)

    fun clearPending(context: Context) {
        context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE).edit()
            .remove("code_verifier").remove("oauth_state").apply()
    }

    private fun newCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
