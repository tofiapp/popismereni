# Měření

Tabletová aplikace pro zapisování měření. Tři horní pole + vyhledávání stanice;
záznam se ukládá do CSV.

**Aktuální verze: `v0.4.0`** ([`VERSION`](VERSION), [`VERSIONING.md`](VERSIONING.md)).

## Pasport na zařízení

Aplikace čte **`DZS_PASPORT_TPI.sqlite` přímo ze tabletu** (ne z Gitu).

1. Soubor dej do `Download/` / `Documents/`, **nebo**
2. V aplikaci klepni **Vybrat SQLite** a vyber soubor (cesta se zapamatuje).
3. **Obnovit** znovu načte DB (po aktualizaci pasportu na zařízení).

Tabulky:
- `DZS_SUPER_RO_TPI` — objekty, `TUDU` (UDU = prvních 5 znaků)
- `DZS_SUPER_MT_SL` — `REPRE_TUDU` + `JMENO` (picker bez `žst.` / `odb.` / `z.`)

JSON v assets je jen nouzové demo, když SQLite na zařízení chybí.

## Obrazovka

- **Stanice** — vyhledávání podle JMENO
- **Pole 1** — koleje | spojky
- **Pole 2** — výhybky
- **Pole 3** — čas (Teď, ±, wheel)

## Aktualizace APK

`applicationId` = `cz.mereni.app` → `adb install -r …mereni-v0.4.0-debug.apk`
