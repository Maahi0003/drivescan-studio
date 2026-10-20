package com.drivescan.kmp.auth

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.drivescan.kmp.data.JsonParser
import com.drivescan.kmp.data.str
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import com.drivescan.shared.DriveApi
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Landing activity for the OAuth redirect
 * (com.drivescan.kmp:/oauth2redirect). Exchanges the authorization code
 * for tokens (with the stored PKCE verifier) on a background thread,
 * then finishes back to whatever screen launched the flow.
 */
class AuthActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        val uri = intent?.data
        val code = uri?.getQueryParameter("code")
        val state = uri?.getQueryParameter("state")
        val error = uri?.getQueryParameter("error")

        when {
            error != null -> {
                toast(trf(Str.AUTH_ERR_REJECTED, error))
                finish()
            }
            code == null -> {
                toast(tr(Str.AUTH_INCOMPLETE))
                finish()
            }
            state == null || state != AuthManager.pendingState(this) -> {
                toast(tr(Str.AUTH_STATE_MISMATCH))
                AuthManager.clearPending(this)
                finish()
            }
            else -> exchangeCode(code)
        }
    }

    private fun exchangeCode(code: String) {
        val verifier = AuthManager.pendingVerifier(this)
        val cid = AuthManager.clientId(this)
        if (verifier == null || cid.isEmpty()) {
            toast(tr(Str.AUTH_SESSION_EXPIRED))
            finish()
            return
        }
        toast(tr(Str.AUTH_CONNECTING))
        thread {
            val result = try {
                val body = listOf(
                    "code" to code,
                    "client_id" to cid,
                    "code_verifier" to verifier,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to DriveApi.REDIRECT_URI
                ).joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                }
                val conn = URL(DriveApi.OAUTH_TOKEN_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code2 = conn.responseCode
                val stream = if (code2 in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                if (code2 !in 200..299) {
                    trf(Str.AUTH_TOKEN_FAIL, code2)
                } else {
                    val parsed = JsonParser(text).parse() as? Map<String, Any?>
                    val access = parsed?.str("access_token")
                    val refresh = parsed?.str("refresh_token")
                    val expiresIn = (parsed?.get("expires_in") as? Number)?.toLong() ?: 3600L
                    if (access == null) {
                        tr(Str.AUTH_NO_TOKEN)
                    } else {
                        AuthManager.saveTokens(this, access, refresh, expiresIn)
                        null // success
                    }
                }
            } catch (e: Exception) {
                trf(Str.AUTH_NETWORK_ERROR, e.message ?: "")
            }
            AuthManager.clearPending(this)
            runOnUiThread {
                toast(if (result == null) tr(Str.AUTH_SUCCESS) else result)
                finish()
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
