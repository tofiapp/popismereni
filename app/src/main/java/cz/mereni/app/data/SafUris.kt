package cz.mereni.app.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.FileInputStream
import java.io.FileNotFoundException

/** Pomocníci pro SAF / OneDrive URI. */
object SafUris {
    fun isOneDrive(uri: Uri): Boolean {
        val a = uri.authority.orEmpty().lowercase()
        val s = uri.scheme.orEmpty().lowercase()
        val path = uri.path.orEmpty().lowercase()
        return "skydrive" in a || "onedrive" in a || a.startsWith("com.microsoft.") ||
            (s == ContentResolver.SCHEME_FILE && "skydrive" in path)
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
     *
     * OneDrive přes Google Files často vrací content URI, které deny, nebo starý file://
     * do privátní cache — to nejde přečíst. Spolehlivé: Sdílet → Měření.
     */
    fun readAllBytes(resolver: ContentResolver, uri: Uri): ByteArray {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            throw IllegalStateException(denyMessage(uri))
        }
        var last: Exception? = null
        val readers: List<() -> ByteArray?> = listOf(
            {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            },
            {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    FileInputStream(afd.fileDescriptor).use { it.readBytes() }
                }
            },
            {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
                }
            },
        )
        for (read in readers) {
            try {
                val bytes = read()
                if (bytes != null) return bytes
            } catch (e: SecurityException) {
                last = e
            } catch (e: FileNotFoundException) {
                last = e
            } catch (e: IllegalArgumentException) {
                last = e
            }
        }
        throw IllegalStateException(denyMessage(uri), last)
    }

    fun denyMessage(uri: Uri): String =
        if (isOneDrive(uri) || uri.scheme == ContentResolver.SCHEME_FILE) {
            "OneDrive přes Files nepustí čtení. " +
                "Použij „Appky (ne Files)…“ a vyber OneDrive, " +
                "nebo v OneDrive: ⋮ → Sdílet / Otevřít v → Měření."
        } else {
            "Nelze otevřít soubor (chybí oprávnění)."
        }
}
