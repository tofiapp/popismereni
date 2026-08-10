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
     * Přečte celý obsah **hned na volajícím vlákně** (ideálně Main v Activity Result).
     * Odložení na Dispatchers.IO u OneDrive často skončí deny — grant je krátký.
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
            "OneDrive odmítl přístup. Zkus tlačítko „Z OneDrive…“, " +
                "nebo v OneDrive appce: Sdílet / Otevřít v → Měření " +
                "(případně stáhni do Download)."
        } else {
            "Nelze otevřít soubor (chybí oprávnění)."
        }
}
