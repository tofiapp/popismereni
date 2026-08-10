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
 * SAF / Download / Sdílet v organizacích často nefungují; Graph je oficiální cesta.
 * Vyžaduje Azure AD registraci aplikace (Client ID) a oprávnění Files.Read —
 * IT musí appku schválit. Politika „block download“ může i Graph content zakázat.
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
        get() = prefs.getString(KEY_PATH, DEFAULT_PATH).orEmpty().ifBlank { DEFAULT_PATH }
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
                app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(activeAccount: com.microsoft.identity.client.IAccount?) {
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
                })
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
     * Stáhne bajty mereni.csv do paměti aplikace (ne do veřejného Download).
     */
    suspend fun downloadMereniCsv(activity: Activity): ByteArray {
        require(hasClientId()) {
            "Chybí Azure Client ID — bez registrace v Azure Graph nejde."
        }
        val token = accessToken(activity)
        return withContext(Dispatchers.IO) {
            val path = filePath.trim().trimStart('/')
            val encoded = path.split('/').joinToString("/") {
                URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
            }
            val direct = "https://graph.microsoft.com/v1.0/me/drive/root:/$encoded:/content"
            when (val first = httpGetBytes(direct, token)) {
                is GraphBody.Ok -> first.bytes
                is GraphBody.Err -> {
                    if (first.code == 404) {
                        val id = searchItemId(token, File(path).name)
                            ?: error(
                                "Soubor „$path“ v OneDrive nenalezen. " +
                                    "Uprav cestu (např. Documents/mereni.csv).",
                            )
                        when (val second = httpGetBytes(
                            "https://graph.microsoft.com/v1.0/me/drive/items/$id/content",
                            token,
                        )) {
                            is GraphBody.Ok -> second.bytes
                            is GraphBody.Err -> throw graphError(second)
                        }
                    } else {
                        throw graphError(first)
                    }
                }
            }
        }
    }

    private suspend fun accessToken(activity: Activity): String {
        val app = withContext(Dispatchers.IO) { pca() }
        val account = suspendCancellableCoroutine { cont ->
            app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: com.microsoft.identity.client.IAccount?) {
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
            })
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

    private fun searchItemId(token: String, name: String): String? {
        val q = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        val url =
            "https://graph.microsoft.com/v1.0/me/drive/root/search(q='$q')?\$select=id,name,file&\$top=10"
        return when (val body = httpGetBytes(url, token)) {
            is GraphBody.Ok -> {
                val text = body.bytes.toString(StandardCharsets.UTF_8)
                val arr = JSONObject(text).optJSONArray("value") ?: return null
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("name").equals(name, ignoreCase = true) && o.has("file")) {
                        return o.getString("id")
                    }
                }
                null
            }
            is GraphBody.Err -> null
        }
    }

    private fun httpGetBytes(url: String, token: String): GraphBody {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "*/*")
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        return try {
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
                "Přístup zamítnut ($err.code). IT musí povolit Files.Read / " +
                    "případně politika „block download“ blokuje i Graph."
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
        const val DEFAULT_PATH = "mereni.csv"

        /** Debug keystore SHA-1 (base64) — shodný v CI i lokálně. */
        const val SIGNATURE_HASH = "yiIoprpAmLGx1r3h3rLOILUdCE0="
        const val REDIRECT_URI = "msauth://cz.mereni.app/$SIGNATURE_HASH"

        val SCOPES = arrayOf("User.Read", "Files.Read")
    }
}
