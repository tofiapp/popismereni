package cz.mereni.app.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * OneDrive přes Microsoft Graph (MSAL).
 *
 * Upload/nahrazení [mereni_MD1.xlsx] — lokál se hromadí, Graph přepíše stejný soubor.
 * Vyžaduje Azure Client ID a oprávnění Files.ReadWrite (IT).
 */
class OneDriveGraph(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, "").orEmpty().trim()
        set(value) {
            prefs.edit().putString(KEY_CLIENT_ID, value.trim()).apply()
            cachedApp = null
        }

    var filePath: String
        get() {
            val stored = prefs.getString(KEY_PATH, DEFAULT_PATH).orEmpty().ifBlank { DEFAULT_PATH }
            // Migrace ze starého CSV importu
            return if (stored.endsWith(".csv", ignoreCase = true)) DEFAULT_PATH else stored
        }
        set(value) {
            prefs.edit().putString(KEY_PATH, value.trim().ifBlank { DEFAULT_PATH }).apply()
        }

    @Volatile
    private var cachedApp: ISingleAccountPublicClientApplication? = null

    fun hasClientId(): Boolean = clientId.length >= 8

    suspend fun signedInAccount(): String? = withContext(Dispatchers.IO) {
        if (!hasClientId()) return@withContext null
        runCatching {
            val app = pca()
            suspendCancellableCoroutine { cont ->
                app.getCurrentAccountAsync(
                    object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                        override fun onAccountLoaded(
                            activeAccount: com.microsoft.identity.client.IAccount?,
                        ) {
                            if (cont.isActive) cont.resume(activeAccount?.username)
                        }

                        override fun onAccountChanged(
                            priorAccount: com.microsoft.identity.client.IAccount?,
                            currentAccount: com.microsoft.identity.client.IAccount?,
                        ) {
                            if (cont.isActive) cont.resume(currentAccount?.username)
                        }

                        override fun onError(exception: MsalException) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
            }
        }.getOrNull()
    }

    suspend fun signIn(activity: Activity): String = withContext(Dispatchers.Main) {
        require(hasClientId()) {
            "Chybí Azure Client ID — zadej ho v nastavení (od IT)."
        }
        val app = withContext(Dispatchers.IO) { pca() }
        val result = acquireTokenInteractive(activity, app)
        result.account.username ?: "přihlášeno"
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        if (!hasClientId()) return@withContext
        val app = pca()
        suspendCancellableCoroutine { cont ->
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(exception: MsalException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            })
        }
    }

    /**
     * Nahraje / nahradí Excel na cestě [filePath] v OneDrive
     * (`PUT /me/drive/root:/{path}:/content`).
     */
    suspend fun uploadOrReplaceXlsx(activity: Activity, bytes: ByteArray): String {
        require(hasClientId()) {
            "Chybí Azure Client ID — bez registrace v Azure Graph nejde."
        }
        require(bytes.isNotEmpty()) { "Prázdný Excel — není co nahrát." }
        val token = accessToken(activity)
        return withContext(Dispatchers.IO) {
            val path = filePath.trim().trimStart('/')
            val encoded = path.split('/').joinToString("/") {
                URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
            }
            val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$encoded:/content"
            when (val result = httpPutBytes(url, token, bytes, MeasurementStore.MIME_XLSX)) {
                is GraphBody.Ok -> path
                is GraphBody.Err -> throw graphError(result)
            }
        }
    }

    private suspend fun accessToken(activity: Activity): String {
        val app = withContext(Dispatchers.IO) { pca() }
        val account = suspendCancellableCoroutine { cont ->
            app.getCurrentAccountAsync(
                object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(
                        activeAccount: com.microsoft.identity.client.IAccount?,
                    ) {
                        if (cont.isActive) cont.resume(activeAccount)
                    }

                    override fun onAccountChanged(
                        priorAccount: com.microsoft.identity.client.IAccount?,
                        currentAccount: com.microsoft.identity.client.IAccount?,
                    ) {
                        if (cont.isActive) cont.resume(currentAccount)
                    }

                    override fun onError(exception: MsalException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                },
            )
        }
        if (account == null) {
            return acquireTokenInteractive(activity, app).accessToken
        }
        return try {
            acquireTokenSilent(app, account).accessToken
        } catch (_: Exception) {
            acquireTokenInteractive(activity, app).accessToken
        }
    }

    private suspend fun acquireTokenInteractive(
        activity: Activity,
        app: ISingleAccountPublicClientApplication,
    ): IAuthenticationResult = suspendCancellableCoroutine { cont ->
        val params = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(activity)
            .withScopes(SCOPES.toList())
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    if (cont.isActive) cont.resume(authenticationResult)
                }

                override fun onError(exception: MsalException) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException(
                                "Přihlášení selhalo: ${exception.message ?: exception.errorCode}",
                                exception,
                            ),
                        )
                    }
                }

                override fun onCancel() {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("Přihlášení zrušeno"))
                    }
                }
            })
            .build()
        app.acquireToken(params)
    }

    private suspend fun acquireTokenSilent(
        app: ISingleAccountPublicClientApplication,
        account: com.microsoft.identity.client.IAccount,
    ): IAuthenticationResult = suspendCancellableCoroutine { cont ->
        val params = AcquireTokenSilentParameters.Builder()
            .forAccount(account)
            .fromAuthority(account.authority)
            .withScopes(SCOPES.toList())
            .withCallback(object : SilentAuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    if (cont.isActive) cont.resume(authenticationResult)
                }

                override fun onError(exception: MsalException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            })
            .build()
        app.acquireTokenSilentAsync(params)
    }

    private suspend fun pca(): ISingleAccountPublicClientApplication {
        cachedApp?.let { return it }
        require(hasClientId()) { "Chybí Client ID" }
        val configFile = writeConfig(clientId)
        val app = suspendCancellableCoroutine { cont ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context.applicationContext,
                configFile,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        if (cont.isActive) cont.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException(
                                    "MSAL: ${exception.message ?: exception.errorCode}",
                                    exception,
                                ),
                            )
                        }
                    }
                },
            )
        }
        cachedApp = app
        return app
    }

    private fun writeConfig(id: String): File {
        val json = """
            {
              "client_id": "$id",
              "authorization_user_agent": "DEFAULT",
              "redirect_uri": "$REDIRECT_URI",
              "account_mode": "SINGLE",
              "broker_redirect_uri_registered": true,
              "authorities": [
                {
                  "type": "AAD",
                  "audience": { "type": "AzureADMultipleOrgs" },
                  "default": true
                }
              ]
            }
        """.trimIndent()
        val file = File(context.filesDir, "msal_config.json")
        file.writeText(json)
        return file
    }

    private fun httpPutBytes(
        url: String,
        token: String,
        body: ByteArray,
        contentType: String,
    ): GraphBody {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Content-Length", body.size.toString())
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        return try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code in 200..299) GraphBody.Ok(bytes) else GraphBody.Err(code, bytes)
        } finally {
            conn.disconnect()
        }
    }

    private fun graphError(err: GraphBody.Err): IllegalStateException {
        val msg = err.bytes.toString(StandardCharsets.UTF_8)
        val hint = when {
            err.code == 401 || err.code == 403 ->
                "Přístup zamítnut (${err.code}). IT musí povolit Files.ReadWrite " +
                    "a schválit appku (Client ID)."
            err.code == 404 ->
                "Cesta nenalezena — zkontroluj cestu v nastavení (např. $DEFAULT_PATH)."
            else -> "Graph HTTP ${err.code}"
        }
        val short = msg.replace('\n', ' ').take(180)
        return IllegalStateException(if (short.isBlank()) hint else "$hint — $short")
    }

    private sealed class GraphBody {
        class Ok(val bytes: ByteArray) : GraphBody()
        class Err(val code: Int, val bytes: ByteArray) : GraphBody()
    }

    companion object {
        private const val PREFS = "onedrive_graph"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_PATH = "file_path"
        const val DEFAULT_PATH = "mereni_MD1.xlsx"

        /** Debug keystore SHA-1 (base64) — shodný v CI i lokálně. */
        const val SIGNATURE_HASH = "yiIoprpAmLGx1r3h3rLOILUdCE0="
        const val REDIRECT_URI = "msauth://cz.mereni.app/$SIGNATURE_HASH"

        val SCOPES = arrayOf("User.Read", "Files.ReadWrite")
    }
}
