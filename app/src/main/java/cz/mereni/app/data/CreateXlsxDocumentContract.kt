package cz.mereni.app.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/** Vstup pro CreateDocument: název souboru + volitelná počáteční složka/URI. */
data class CreateXlsxRequest(
    val fileName: String,
    val initialUri: Uri? = null,
)

/**
 * „Uložit jako…“ s předvyplněným `YYMMDD_N_MD1.xlsx` a tipem na poslední složku.
 */
class CreateXlsxDocumentContract : ActivityResultContract<CreateXlsxRequest, Uri?>() {
    override fun createIntent(context: Context, input: CreateXlsxRequest): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MeasurementStore.MIME_XLSX
            putExtra(Intent.EXTRA_TITLE, input.fileName)
            if (input.initialUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.initialUri)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
