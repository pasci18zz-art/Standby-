# MKW Streckenzentrale

Persönliche, inoffizielle Fan-Datenbank für **Mario Kart World**: Weltkarte → Strecke → Variante (Hauptstrecke, Intermission, SNES) → Detailseite mit Bewertung (1–10), Sieges-Schalter und Notizen.

- `index.html` – direkt im Browser öffnen (alles in einer Datei, keine Installation)
- `src/app.html` + `src/routes.js` – Quelltext; `python3 build.py` erzeugt daraus `index.html` und `streckenzentrale.html`

## Datenumfang
- 30 Hauptstrecken mit Cups (Pilz- bis Spezial-Cup) und Position auf der Weltkarte
- 202 Intermissions (Start → Ziel, Anzahl Abschnitte, Einbahn-Kennzeichnung)
- 10 SNES-Strecken aus Update 1.8.0, jeweils der Gastgeberstrecke zugeordnet
  (Mario Circuit: MC 1–3 · Boo Cinema: Ghost Valley 1–3 · Choco Mountain: Choco Island 1–2 ·
  Sky-High Sundae: Vanilla Lake 1 · Koopa Troopa Beach: Koopa Beach 1)

## Speicherung
Bewertungen, Siege und Notizen werden im Browser (localStorage) gespeichert; als Claude-Artifact zusätzlich privat im eigenen Claude-Konto.
Eigene Streckenbilder liegen nur im Browser (IndexedDB). Unter „Fortschritt → Sicherung“ lässt sich alles exportieren und importieren.

Karte und Streckenlayouts sind eigene, schematische Zeichnungen (keine Original-Grafiken). Echte Streckenkarten können pro Variante als eigenes Bild hochgeladen werden.
