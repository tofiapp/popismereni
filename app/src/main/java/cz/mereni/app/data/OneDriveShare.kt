package cz.mereni.app.data

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Starý (ověřený) způsob: ACTION_SEND + chooser „Uložit na OneDrive“.
 *
 * Na pracovním profilu Files / SAF OneDrive často schová; share sheet
 * OneDrive appku obvykle nabídne (stejně jako před v0.46).
 */
object OneDriveShare {

    private val ONEDRIVE_PACKAGES = listOf(
        "com.microsoft.skydrive",
        "com.microsoft.office.onedrive",
    )

    fun shareExport(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MeasurementStore.MIME_XLSX
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TITLE, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        }

        // OneDrive nahoru v chooseru (když je nainstalovaný) — bez přímého startu,
        // ať zůstane stejné chování jako dřív (uživatel klikne OneDrive).
        val initial = ONEDRIVE_PACKAGES.mapNotNull { pkg ->
            if (!isPackageInstalled(context, pkg)) return@mapNotNull null
            Intent(send).apply { setPackage(pkg) }
                .takeIf { it.resolveActivity(context.packageManager) != null }
                ?.also {
                    try {
                        context.grantUriPermission(
                            pkg,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: Exception) {
                    }
                }
        }

        val chooser = Intent.createChooser(send, "Uložit na OneDrive").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (initial.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, initial.toTypedArray())
            }
        }
        context.startActivity(chooser)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
