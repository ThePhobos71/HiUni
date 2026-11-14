# Lieblingsgericht-Alarm (Design)

Stand: 2026-11-14. Kleine Änderung, kurzes Design.

## Absicht

Wer ein Lieblingsgericht hat (Beispiel: Currywurst), soll nicht jeden Tag den
Speiseplan durchsehen müssen. Die App meldet sich einmal, sobald das Gericht im
Plan der gewählten Mensa auftaucht.

## Ziele

- Stichwörter merken, entweder per Stern an einem Gericht oder frei eingetippt.
- Eine Mitteilung, sobald ein Treffer neu im Speiseplan steht, mit Gericht und Tag
  ("Currywurst am Donnerstag").
- Abschaltbar in den Mitteilungs-Einstellungen, wie die anderen Alarme.

## Nicht-Ziele

- Keine Empfehlungen, kein Lernen aus dem Essverhalten.
- Kein Abgleich über mehrere Mensen gleichzeitig: es zählt die in den
  Einstellungen gewählte Mensa.
- Kein eigener Hintergrund-Abruf nur für diesen Alarm.

## Anti-Ziele

- Dieselbe Mitteilung darf nicht bei jedem Aktualisieren erneut kommen.
- Keine Emojis in Mitteilungs- und UI-Texten.

## Rahmenbedingungen (Ist-Zustand)

- `MensaRepository.refresh()` lädt den Plan für 14 Tage in die Tabelle `meals`
  (`MealEntity`), gedrosselt auf 6 Stunden.
- Der `PrefetchOrchestrator` stößt den Refresh beim App-Start und über den
  Push-Sync-Worker an.
- `NotificationKind.MENSA` und die Kategorie-Schalter im
  `NotificationSettingsStore` existieren bereits.
- Vorbild für "nur einmal melden": `BibFreeSlotNotifier` mit eigenem Store.

## Wege

1. **Stichwörter (gewählt):** Gespeichert wird ein Stichwort, der Abgleich prüft,
   ob es im Namen des Gerichts vorkommt. Robust, weil die Mensa dasselbe Essen
   unterschiedlich benennt ("Currywurst mit Pommes", "Currywurst vom Schwein").
2. **Exakte Gerichte merken:** Stern speichert genau dieses Gericht. Einfacher,
   verfehlt aber jede abweichende Schreibweise. Verworfen.
3. **Kategorien oder Tags als Favorit** (zum Beispiel "vegan"): trifft fast jeden
   Tag und wäre kein Alarm mehr. Verworfen.

Kjell hat Weg 1 am 2026-11-14 bestätigt.

## Entwurf

- **Speicher:** Liste der Stichwörter im Preferences-DataStore, dazu die Menge
  der bereits gemeldeten Treffer (Stichwort plus `sourceId` des Gerichts).
  Gemeldete Treffer für vergangene Tage werden aufgeräumt.
- **Abgleich:** nach jedem erfolgreichen Refresh über das 14-Tage-Fenster der
  gewählten Mensa. Groß- und Kleinschreibung egal, Treffer im Gerichtsnamen.
- **Mitteilung:** Art `MENSA`. Ein Treffer: "Currywurst am Donnerstag". Mehrere
  neue Treffer in einem Durchlauf werden zu einer Mitteilung gebündelt. Tippen
  öffnet den Mensa-Tab (der Tag steht im Mitteilungstext; ein Sprung direkt auf
  den Tag würde einen Umbau der Deep-Link-Signale verlangen).
- **UI:** Stern in der Gericht-Ansicht schlägt den Gerichtsnamen als Stichwort
  vor (kürzbar). Eine Liste "Lieblingsgerichte" in den Mensa-Einstellungen zum
  Hinzufügen und Entfernen. Handgestylte Surface-und-Text-Pills wie im restlichen Design.
- **Schalter:** eigener Eintrag in den Mitteilungs-Einstellungen.

## Dem Bau überlassen (meine Festlegungen, nicht von Kjell entschieden)

- Abgleich nur im Namen, nicht in Beschreibung oder englischem Namen.
- Bündelung mehrerer Treffer zu einer Mitteilung.
- Genaue Platzierung von Stern und Liste im Mensa-Screen, nach Blick in den
  Design-Mock.
- Beim Hinzufügen eines Stichworts werden schon im Plan stehende Treffer sofort
  gemeldet beziehungsweise direkt in der Liste angezeigt.

## Tests

- Abgleich als reine Funktion (Stichwörter, Gerichte, bereits Gemeldetes ergibt
  neue Treffer), per JVM-Unit-Test.
- Kein doppeltes Melden nach zweitem Refresh.
- Abgeschalteter Schalter unterdrückt die Mitteilung.
