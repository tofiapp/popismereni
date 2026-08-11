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
 * Sdílení na OneDrive bez Google Files / SAF.
 *
 * Na pracovním profilu Files často hlásí „administrátor nepovoluje ukládat
 * osobní věci…“ a OneDrive v pickeru chybí. Share Intent přímo do appky
 * OneDrive to typicky obejde (stejně jako dřív).
 */
object OneDriveShare {

    /** Běžné balíčky OneDrive na Androidu. */
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
        val send = buildSendIntent(context, uri, file.name)
        val activityFlags = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK

        for (pkg in ONEDRIVE_PACKAGES) {
            if (!isPackageInstalled(context, pkg)) continue
            val direct = Intent(send).apply { setPackage(pkg) }
            if (direct.resolveActivity(context.packageManager) == null) continue
            try {
                context.grantUriPermission(
                    pkg,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            context.startActivity(direct.addFlags(activityFlags))
            return
        }

        // OneDrive appka nenalezena / nebere SEND → obecný chooser
        val chooser = Intent.createChooser(
            send,
            "Uložit na OneDrive — vyber OneDrive (ne Files)",
        ).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(activityFlags)
        }
        context.startActivity(chooser)
    }

    private fun buildSendIntent(context: Context, uri: Uri, fileName: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = MeasurementStore.MIME_XLSX
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
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
