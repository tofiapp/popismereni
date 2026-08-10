package cz.mereni.app.data

import android.app.Activity
import android.content.ComponentName
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
 * DocumentsUI / Google Files — na tabletech často jediný „picker“ u GET_CONTENT,
 * takže createChooser vypadá stejně jako OpenDocument. Pro výběr cloud app je vyloučíme.
 */
fun documentsUiExcludeComponents(context: Context): Array<ComponentName> {
    val pm = context.packageManager
    val probes = listOf(
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        },
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        },
    )
    val names = linkedSetOf<ComponentName>()
    for (probe in probes) {
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        @Suppress("DEPRECATION")
        val ris = pm.queryIntentActivities(probe, flags)
        for (ri in ris) {
            val pkg = ri.activityInfo.packageName
            if (
                pkg.contains("documentsui", ignoreCase = true) ||
                pkg == "com.google.android.apps.nbu.files"
            ) {
                names.add(ComponentName(pkg, ri.activityInfo.name))
            }
        }
    }
    return names.toTypedArray()
}

/** Spustí OneDrive appku (pro Sdílet → Měření). */
fun launchOneDriveApp(context: Context): Boolean {
    val launch = context.packageManager.getLaunchIntentForPackage(ONEDRIVE_PACKAGE) ?: return false
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(launch)
        true
    }.getOrDefault(false)
}

/**
 * GET_CONTENT v createChooser **bez** Google Files / DocumentsUI —
 * uživatel vidí OneDrive, Drive, … jako samostatné appky.
 */
class GetContentChooser(private val title: String) : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        val get = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input
            putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(get, title).apply {
            val exclude = documentsUiExcludeComponents(context)
            if (exclude.isNotEmpty()) {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, exclude)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
            ?: intent?.clipData?.takeIf { resultCode == Activity.RESULT_OK }?.getItemAt(0)?.uri
}

/**
 * Otevře přímo OneDrive (GET_CONTENT s package).
 * Spolehlivější než Google Files → OneDrive provider (ten často deny).
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
            ?: intent?.clipData?.takeIf { resultCode == Activity.RESULT_OK }?.getItemAt(0)?.uri

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
