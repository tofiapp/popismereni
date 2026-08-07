# Měření

**v0.5.0** — pasport ze zařízení, rychlé hledání stanic, klávesy podle UDU.

## Pasport na tabletu

Aplikace **sama hledá** `DZS_PASPORT_TPI.sqlite` v Download/Documents (a MediaStore).
Najde-li ho, zkopíruje si ho a čte z něj stanice + klávesy.

1. Dej soubor do `Download/`
2. Spusť aplikaci (případně povol přístup k úložišti)
3. Vyhledej stanici (min. 2 písmena) → načtou se koleje/spojky/výhybky jen pro ni

Tlačítka **Vybrat** / **Obnovit** jsou záloha.

## Data

- `DZS_SUPER_RO_TPI.TUDU` → UDU = prvních 5 znaků
- `DZS_SUPER_MT_SL.REPRE_TUDU` + `JMENO` (bez `žst.` / `odb.` / `z.`)
- Klávesy se načítají SQL filtrem podle UDU (ne celá DB najednou)

## APK

`adb install -r …mereni-v0.5.0-debug.apk` (`applicationId` = `cz.mereni.app`)
