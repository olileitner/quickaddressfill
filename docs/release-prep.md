# HouseNumberClick Release Prep

## Release v1.0.4 (Naming Correction)

- Korrigiert sichtbare Release-Benennung auf `HouseNumberClick` (statt altem Pluginnamen).
- Keine funktionale Verhaltensaenderung im Plugin-Workflow.
- Folge-Release zu `v1.0.3`, damit Store/GitHub-Darstellung konsistent bleibt.

## A. Release Notes (Entwurf)

### Technischer Fokus dieses Release-Kandidaten

- Mehrere kleine, verhaltensgleiche Struktur-Refactorings entlasten den bisherigen Monolithen im Street-MapMode.
- Fachlogik wurde in dedizierte Services ausgelagert (Resolver, House-Number-Regeln, Readback, Konfliktanalyse, Dialogmodell-Aufbereitung), ohne den Benutzer-Workflow zu ändern.
- Fehlerdiagnose in kritischen Pfaden wurde verbessert; relevante Klickpfad- und Integrationsinformationen sind über Debug-Logs nachvollziehbarer.

### Stabilitätsverbesserungen

- Robusterer Klickpfad mit klarerer Deduplizierung für echte Release-Dubletten.
- Kandidaten-Scans in dichtem Gebiet sind begrenzt und diagnostizierbar.
- Datensatzwechsel invalidiert gemerkte Dialogkontexte kontrolliert.
- Address-Handoff für BuildingSplitter wurde gegen stale Zustand gehärtet.

### Debug-/Diagnoseverbesserungen

- Debug-Logs für Klickpfad enthalten unter anderem Outcome, Trefferquelle, Kandidatenzahlen, Limit-Flags und Laufzeit.
- Kritische Fehlerpfade loggen konsistenter mit Kontext.

### Konfigurierbare Scan-Limits

- `housenumberclick.streetmode.relationScanLimit` (Standard: `3000`)
- `housenumberclick.streetmode.wayScanLimit` (Standard: `5000`)

Ungültige Werte fallen auf sichere Defaults zurück und werden protokolliert.

### BuildingSplitter-Handoff

- Reflection-Handoff bleibt bevorzugter Pfad.
- Preference-Fallback besitzt Session-/Timestamp-Härtung zur Vermeidung stale pending Daten.

## B. QA-Checkliste vor Release

1. Apply-Flow
   - Dialogwerte setzen, mehrere Gebäude klicken.
   - Erwartet: Tags korrekt gesetzt, Hausnummernfortschritt wie konfiguriert.

2. Ctrl+Click Readback
   - Ctrl+Click auf getaggtes Gebäude und auf benannte Straße.
   - Erwartet: Gebäude-Readback lädt `street/postcode/housenumber`; Street-Pickup setzt Hausnummer auf `1`.

3. Overwrite/Suppression
   - Konfliktfall auslösen, einmal abbrechen, danach akzeptieren mit Suppression.
   - Erwartet: Abbruch stoppt Apply; Suppression unterdrückt Folgewarnung für dieselbe Straße.

4. Dataset-Wechsel
   - Werte in Dataset A setzen, auf Dataset B wechseln, Dialog öffnen.
   - Erwartet: keine unkontrollierte Übernahme gemerkter Werte aus A.

5. BuildingSplitter-Handoff
   - Split-Flow mit BuildingSplitter verfügbar und optional nicht verfügbar testen.
   - Erwartet: sauberer Handoff bzw. saubere Fehler-/Fallback-Reaktion.

6. Dichtes Gebiet / Kandidatenlimits
   - Test mit Standard- und bewusst niedrigen Limits.
   - Erwartet: bei niedrigen Limits mehr Misses; bei Standard gute Balance aus Trefferquote/Reaktionszeit.

## C. Known Issues / Support-Hinweise

- Hohe Datendichte kann Erkennung und Reaktionszeit beeinflussen; zu niedrige Scan-Limits erhöhen False-Negatives.
- Interoperabilität mit externen Plugins (insb. BuildingSplitter) bleibt versions-/update-abhängig.
- Für Diagnose Debug-Logs aktivieren und folgende Keys prüfen:
  - `housenumberclick.streetmode.relationScanLimit`
  - `housenumberclick.streetmode.wayScanLimit`
  - `housenumberclick.buildingsplitter.forcePreferenceFallback`
  - `housenumberclick.buildingsplitter.handoff.street`
  - `housenumberclick.buildingsplitter.handoff.postcode`
  - `housenumberclick.buildingsplitter.handoff.pending`
  - `housenumberclick.buildingsplitter.handoff.timestamp`
  - `housenumberclick.buildingsplitter.handoff.session`

