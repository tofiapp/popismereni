package cz.mereni.app.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/** Pomocníci pro SAF / OneDrive URI. */
object SafUris {
    fun isOneDrive(uri: Uri): Boolean {
        val a = uri.authority.orEmpty().lowercase()
        return "skydrive" in a || "onedrive" in a || a.startsWith("com.microsoft.")
    }

    /**
     * Persistovatelná oprávnění OneDrive DocumentsProvider typicky **nepodporuje**
     * a `takePersistableUriPermission` u něj často skončí deny při dalším čtení.
     * Pro jednorázový import stačí dočasný grant z pickeru / share.
     */
    fun takePersistableReadIfUseful(resolver: ContentResolver, uri: Uri) {
        if (isOneDrive(uri)) return
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Provider nepodporuje persist — OK u jednorázového čtení
        } catch (_: Exception) {
        }
    }

    /**
     * Přečte celý obsah hned (dokud platí dočasný grant).
     * Při deny od OneDrive hodí srozumitelnou chybu.
     */
    fun readAllBytes(resolver: ContentResolver, uri: Uri): ByteArray {
        return try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error(denyMessage(uri))
        } catch (e: SecurityException) {
            throw IllegalStateException(denyMessage(uri), e)
        }
    }

    fun denyMessage(uri: Uri): String =
        if (isOneDrive(uri)) {
            "OneDrive odmítl přístup (SAF). Z OneDrive appky: Sdílet / Otevřít v → Měření, " +
                "nebo soubor nejdřív stáhni do Download a načti odtud."
        } else {
            "Nelze otevřít soubor (chybí oprávnění)."
        }
}
