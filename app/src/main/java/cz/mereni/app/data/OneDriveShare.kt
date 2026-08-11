package cz.mereni.app.data

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Share sheet:
 * - OneDrive dostane **xlsx** (uložit denní soubor)
 * - Edge dostane **zip** (xlsx Edge přes VIEW neumí — zip otevře / stáhne)
 */
object OneDriveShare {

    private val ONEDRIVE_PACKAGES = listOf(
        "com.microsoft.skydrive",
        "com.microsoft.office.onedrive",
    )

    private val EDGE_PACKAGES = listOf(
        "com.microsoft.emmx",
    )

    const val MIME_ZIP = "application/zip"

    fun shareExport(context: Context, file: File) {
        val xlsxUri = fileProviderUri(context, file)
        val zipFile = zipBeside(file)
        val zipUri = fileProviderUri(context, zipFile)

        val sendXlsx = Intent(Intent.ACTION_SEND).apply {
            type = MeasurementStore.MIME_XLSX
            putExtra(Intent.EXTRA_STREAM, xlsxUri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TITLE, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.name, xlsxUri)
        }

        val initial = ArrayList<Intent>()

        for (pkg in ONEDRIVE_PACKAGES) {
            targetedSend(context, sendXlsx, xlsxUri, pkg)?.let { initial.add(it) }
        }
        for (pkg in EDGE_PACKAGES) {
            edgeZipIntent(context, zipUri, zipFile.name, pkg)?.let { initial.add(it) }
        }

        // Základ chooseru: xlsx (OneDrive / Excel / …). Edge je v INITIAL jako zip.
        val chooser = Intent.createChooser(sendXlsx, "OneDrive (xlsx) nebo Edge (zip)").apply {
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

    /** Zip vedle xlsx (stejný název .zip) — Edge / e-mail. */
    fun zipBeside(xlsx: File): File {
        val zip = File(xlsx.parentFile, xlsx.nameWithoutExtension + ".zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { zos ->
            zos.putNextEntry(ZipEntry(xlsx.name))
            xlsx.inputStream().use { input -> input.copyTo(zos) }
            zos.closeEntry()
        }
        return zip
    }

    private fun edgeZipIntent(
        context: Context,
        zipUri: Uri,
        displayName: String,
        packageName: String,
    ): Intent? {
        if (!isPackageInstalled(context, packageName)) return null
        grantRead(context, packageName, zipUri)

        // VIEW zip — Edge umí stáhnout / otevřít archiv
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(zipUri, MIME_ZIP)
            setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, displayName, zipUri)
        }
        if (view.resolveActivity(context.packageManager) != null) return view

        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_ZIP
            putExtra(Intent.EXTRA_STREAM, zipUri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            putExtra(Intent.EXTRA_TITLE, displayName)
            setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, displayName, zipUri)
        }
        return send.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private fun fileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

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
