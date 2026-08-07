# Měření v0.6.0

## Pasport
Aplikace hledá `DZS_PASPORT_TPI.sqlite` přes MediaStore (Download) a zkopíruje si ho.
Když ho nevidí: **Vybrat** (jednou) — URI si zapamatuje.

Klávesy se načítají na pozadí 3 SQL dotazy (výhybky / spojky / koleje) podle `TUDU LIKE 'UDU%'`.

## UI
- Vyhledávání stanic v **dialogu** (nerozbíjí layout)
- 3 vyšší pole, chipy scrollovatelné
- Podkoleje: dialog po klepnutí na ▾
