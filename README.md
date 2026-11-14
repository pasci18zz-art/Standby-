# Standby

Standby-Anzeige für Android im Querformat (angelehnt an iOS StandBy), z. B. auf dem Induktiv-Ladegerät.

## Funktionen
- **Uhren** (wählbar): Linien (bunt), Neon-Kontur, Klappzahlen, Minimal – Farben und Hintergrund einstellbar
- **Musik**: Cover, Titel, Steuerung und Spulen für jeden Player (Spotify usw.); erscheint nur, wenn Musik läuft
- **Seiten per Wischen**: Uhr (+ Mini-Player) → Musik-Vollbild → Uhr + Musik/Aufgaben → Infos
- **Infos**: Wetter (Open-Meteo), Aufgaben und Klausuren aus der Schulplaner-App (gleiches Supabase-Konto)
- **Nachtmodus** mit Zeitplan, Dimmen, Pixel-Verschiebung gegen Einbrennen
- Antippen blendet Akku, Helligkeit und Einstellungen ein

## Installieren
Jeder Push baut automatisch eine APK → Releases → **„neueste-version“** → `Standby-XX.apk` auf dem Handy laden und installieren.

Beim ersten Start: Einstellungen (Zahnrad) → Musik → **Zugriff erlauben**.

### Optional: Secrets (Settings → Secrets → Actions)
- `SUPABASE_URL`, `SUPABASE_KEY` – wie im Schulplaner, dann muss man sie nicht in der App eintippen
- `SIGNING_KEYSTORE_BASE64`, `SIGNING_PASSWORD` – fester Signierschlüssel
