package cz.mereni.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.documentfile.provider.DocumentFile

/**
 * OpenDocumentTree s explicitními grant flagy (čtení+zápis+persist).
 * U OneDrive na work profilu často stejně nejde — volající musí umět fallback na share.
 */
class OpenDnyTreeContract : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            if (input != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != android.app.Activity.RESULT_OK) return null
        return intent?.data
    }
}

/** Jednorázová zkouška zápisu do vybrané tree URI. */
object DnyFolderProbe {
    fun canWrite(context: Context, treeUri: Uri): Boolean {
        return try {
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            if (!dir.canWrite()) return false
            val name = ".mereni_write_probe"
            dir.findFile(name)?.delete()
            val created = dir.createFile("text/plain", name) ?: return false
            try {
                SafUris.writeAllBytes(context.contentResolver, created.uri, byteArrayOf(0x6D))
                true
            } finally {
                created.delete()
            }
        } catch (_: Exception) {
            false
        }
    }
}
