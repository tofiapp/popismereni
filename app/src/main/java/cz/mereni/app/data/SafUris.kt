package cz.mereni.app.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream

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
     * Pokus o persist čtení+zápis (složka Dny). U OneDrive často failne —
     * volající musí umět spadnout na CreateDocument.
     * @return true pokud se grant povedl (nebo už existuje)
     */
    fun takePersistableReadWrite(resolver: ContentResolver, uri: Uri): Boolean {
        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            resolver.takePersistableUriPermission(uri, flags)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
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

    /**
     * Zapíše bajty **hned na volajícím vlákně** (Activity Result / Main).
     * Stejně jako u čtení — odklad na IO u OneDrive často deny.
     */
    fun writeAllBytes(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            throw IllegalStateException(denyMessage(uri))
        }
        var last: Exception? = null
        val writers: List<() -> Boolean> = listOf(
            {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(bytes)
                    out.flush()
                    true
                } ?: false
            },
            {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                    true
                } ?: false
            },
            {
                resolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    true
                } ?: false
            },
        )
        for (write in writers) {
            try {
                if (write()) return
            } catch (e: SecurityException) {
                last = e
            } catch (e: FileNotFoundException) {
                last = e
            } catch (e: IllegalArgumentException) {
                last = e
            } catch (e: Exception) {
                last = e
            }
        }
        throw IllegalStateException(
            if (isOneDrive(uri)) {
                "OneDrive nepustil zápis (ve Files / SAF na work profilu často chybí). " +
                    "Použij Uložit na OneDrive → sdílení do appky OneDrive."
            } else {
                "Nelze uložit soubor (chybí oprávnění)."
            },
            last,
        )
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
