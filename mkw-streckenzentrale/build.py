#!/usr/bin/env python3
"""Baut die App: fügt die Routendaten ein und erzeugt
 - streckenzentrale.html  (Artifact-Fassung, ohne eigenes Grundgerüst)
 - index.html             (eigenständige Datei zum direkten Öffnen im Browser)"""
from pathlib import Path
d = Path(__file__).parent
app = (d/'src/app.html').read_text(encoding='utf-8').replace('/*ROUTES*/', (d/'src/routes.js').read_text(encoding='utf-8'))
(d/'streckenzentrale.html').write_text(app, encoding='utf-8')
(d/'index.html').write_text('<!doctype html>\n<html lang="de">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">\n<style>[hidden]{display:none!important}body{margin:0}img{max-width:100%}</style>\n</head>\n<body>\n' + app + '\n</body>\n</html>\n', encoding='utf-8')
print('ok')
