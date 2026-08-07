# Měření v0.11.0

## Aktualizace na tabletu (bez přeinstalace)
Debug APK se podepisuje stejným klíčem (`app/keystore/mereni-debug.jks`):

```bash
adb install -r artifacts/mereni-v0.11.0-debug.apk
```

## UI v0.11.0
- Picker koleje: velká karta jen pro číslo bez písmen; varianty A/B/… vedle sebe podle počtu
- Stálá tlačítka výhybek: **po vůz**, **kkk**, **obsazeno**, **vyloučeno** (poslední dvě červeně)
- Čas je vždy jen volitelný (nevyžaduje se při uložení)
- Poznámka + Uložit uprostřed; `<>` zapne šipky přesunu chipů
