package cz.mereni.app.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** OneDrive Android package. */
const val ONEDRIVE_PACKAGE = "com.microsoft.skydrive"

fun isOneDriveInstalled(context: Context): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(ONEDRIVE_PACKAGE, 0)
        true
    }.getOrDefault(false)

/**
 * GET_CONTENT v createChooser — na tabletech jinak skoro vždy spadne do Google Files
 * stejně jako OpenDocument.
 */
class GetContentChooser(private val title: String) : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        val get = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input
            putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(get, title)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
}

/**
 * Otevře přímo OneDrive appku (GET_CONTENT s package).
 * Grant bývá spolehlivější než přes Google Files → OneDrive provider.
 */
class OpenOneDriveDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input
            setPackage(ONEDRIVE_PACKAGE)
            putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data

    companion object {
        fun canResolve(context: Context, mime: String = "*/*"): Boolean {
            if (!isOneDriveInstalled(context)) return false
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mime
                setPackage(ONEDRIVE_PACKAGE)
            }
            return context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .isNotEmpty()
        }
    }
}
