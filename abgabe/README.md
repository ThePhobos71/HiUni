# MSE Project Report — LaTeX-Vorlage

1:1-Nachbau der Word-Vorlage `template.docx` (Mobile Software Engineering,
Prof. Dr. Marc Hesenius, EnIA / Uni Hildesheim).

## Dateien

| Datei | Zweck |
| --- | --- |
| `main.tex` | Der Bericht — hier wird geschrieben. Enthält 1:1 alle Abschnitte, Hinweistexte und Tabellen der Vorlage. |
| `msereport.cls` | Dokumentklasse: Seitenlayout, Kopf-/Fußzeile, Überschriften, Aufzählungen, Tabellenstil. Muss man normalerweise nicht anfassen. |
| `assets/enia-logo.png` | EnIA-Logo der Kopfzeile (aus der .docx extrahiert). |
| `assets/example-architecture.png` | Beispiel-Architekturdiagramm aus Abschnitt 3 — beim Schreiben durch das eigene Diagramm ersetzen. |
| `template.docx` | Original-Vorlage als Referenz. |

## Bauen

```sh
latexmk -pdf main.tex        # pdfLaTeX, Arial-Ersatz Nimbus Sans
latexmk -lualatex main.tex   # LuaLaTeX, echtes Arial (falls installiert)
latexmk -c                   # Hilfsdateien aufräumen
```

Benötigt TeX Live 2021+ (`tabularray`, `titlesec`, `fancyhdr`, `enumitem`,
`geometry`, `helvet`). Beide Wege erzeugen dasselbe Layout.

## Woher die Maße kommen

Alle Werte sind direkt aus dem OOXML der `.docx` übernommen und im Quelltext
als Kommentar belegt (Word-Twips = 1/1440 in, `w:sz` = halbe Punkt):

* Seite A4, Ränder 2 cm links/rechts, Kopf ab 1,25 cm, Fuß ab 1,25 cm vom Rand
* Grundschrift Arial 11 pt in `#444444`, Zeilenabstand einfach (= 1,15 × Größe)
* Überschrift 1: 18 pt fett `#B31B1B` + 0,75-pt-Linie, Abstand 18 pt / 12 pt
* Überschrift 2: 13 pt fett `#B31B1B` + 0,75-pt-Linie, Abstand 12 pt / 4 pt
* Nummer und Text der Überschriften trennt der Word-Standardtabulator (1,27 cm)
* Aufzählungen: Bullet bei 0,635 cm, Text bei 1,27 cm, keine Absatzabstände
* Tabellen: 16,51 cm breit, Rahmen 0,125 pt `#BFBFBF`, Zellrand 4 pt / 6 pt,
  Kopfzeile weiß-fett 10 pt auf `#C00000` bzw. `#B31A1B` (die Vorlage benutzt
  beide Rottöne gemischt — das ist hier genauso übernommen)

## Bewusste Abweichungen

1. **Tabellenköpfe werden nach einem Seitenumbruch wiederholt.** In der `.docx`
   ist `w:tblHeader` nur bei der Team-Tabelle gesetzt; ohne Wiederholung bleibt
   sonst eine einzelne Kopfzeile am Seitenende stehen. Pro Tabelle mit
   `rowhead=0` abschaltbar.
2. **Das leere eingebettete Word-Objekt am Dokumentende** (ein OLE-`Word.Document.12`
   ohne Inhalt, offenbar ein Versehen in der Vorlage) ist nicht nachgebaut.

Gemessene Positionen im erzeugten PDF gegen Word: Logo-Oberkante 1,52 cm
(Word 1,52), rote Kopflinie 3,43 cm (3,45), Unterkante Fußzeile 28,42 cm
(28,45), Tabellenbreite 16,51 cm ab linkem Rand (16,51).

## Eigene Tabellen ergänzen

```latex
\begin{msetblr}[l]{
  colspec={Q[l,wd=4cm] Q[l,wd=6cm]},
  row{1}={bg=msetablered},
}
  Spalte A & Spalte B \\
  \msestrut & \\        % \msestrut gibt leeren Zeilen die Word-Zeilenhöhe
\end{msetblr}
```

Das `[l]` ist nötig — `tabularray` zentriert Tabellen sonst.
Die `wd`-Werte sind die Word-Spaltenbreiten minus 2 × 6 pt Zellrand.

## Bausteine aus der Klasse

| Makro | Bedeutung |
| --- | --- |
| `\msetitleblock{Titel}{Untertitel}` | Titelblock der ersten Seite |
| `\msehint{…}` | kursiver Hinweistext 10,5 pt |
| `\msehintb{…}` | fetter Hinweistext 10,5 pt |
| `\msegap` | Leerabsatz mit 6 pt davor/danach (der häufigste Abstand der Vorlage) |
| `\mseblank[<Höhe>]` | Leerabsatz gegebener Zeilenhöhe |
| `\msevspace{<Maß>}` | reiner Vertikalabstand |
| `\msestrut` | Zeilenhöhen-Strut für leere Tabellenzellen |
