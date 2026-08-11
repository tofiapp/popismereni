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
 * Share sheet: Uložit na OneDrive + otevřít v Microsoft Edge.
 *
 * Na pracovním profilu Files / SAF OneDrive často schová; share sheet
 * OneDrive i Edge obvykle nabídne.
 */
object OneDriveShare {

    private val ONEDRIVE_PACKAGES = listOf(
        "com.microsoft.skydrive",
        "com.microsoft.office.onedrive",
    )

    /** Microsoft Edge — otevření xlsx v prohlížeči. */
    private val EDGE_PACKAGES = listOf(
        "com.microsoft.emmx",
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

        val initial = ArrayList<Intent>()

        for (pkg in ONEDRIVE_PACKAGES) {
            targetedSend(context, send, uri, pkg)?.let { initial.add(it) }
        }
        for (pkg in EDGE_PACKAGES) {
            edgeOpenIntent(context, uri, pkg)?.let { initial.add(it) }
        }

        val chooser = Intent.createChooser(send, "OneDrive nebo otevřít v Edge").apply {
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

    /** Preferuj VIEW (prohlížeč); když Edge neumí VIEW na xlsx, SEND. */
    private fun edgeOpenIntent(context: Context, uri: Uri, packageName: String): Intent? {
        if (!isPackageInstalled(context, packageName)) return null
        grantRead(context, packageName, uri)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MeasurementStore.MIME_XLSX)
            setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "xlsx", uri)
        }
        if (view.resolveActivity(context.packageManager) != null) return view

        val edgeSend = Intent(Intent.ACTION_SEND).apply {
            type = MeasurementStore.MIME_XLSX
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "xlsx", uri)
        }
        return edgeSend.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private fun targetedSend(
        context: Context,
        base: Intent,
        uri: Uri,
        packageName: String,
    ): Intent? {
        if (!isPackageInstalled(context, packageName)) return null
        grantRead(context, packageName, uri)
        return Intent(base).apply { setPackage(packageName) }
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private fun grantRead(context: Context, packageName: String, uri: Uri) {
        try {
            context.grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
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
