### Task 12 Report: ReviewBadge in Meal-Card integriert

**Commit:** `8b7a1b5 feat(reviews): ReviewBadge in Meal-Detail + temporäre Self-Trust`
**Branch:** `feature/mensa-reviews`

---

#### Files Changed

| File | Status |
|------|--------|
| `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewBadge.kt` | Created |
| `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewBadgeViewModel.kt` | Created |
| `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/NutritionParser.kt` | Created |
| `app/src/main/java/de/transio/hiuni/feature/mensa/review/ui/ReviewViewModel.kt` | Modified (TrustDao + MyKeyManager Injection + Self-Trust-Hack) |
| `app/src/main/java/de/transio/hiuni/feature/mensa/ui/MealDetailSheet.kt` | Modified (recipeHash + ReviewBadge + ReviewBottomSheet) |

Unstaged repo-Drift (calendar/learnweb/search/datastore) wurde nicht in den Commit gezogen — gehört nicht zu Task 12.

---

#### Insertion Point: MealDetailSheet (NICHT MealCard)

Begründung:
- `MealCards.kt:204` (`MealCard`) rendert Kompakt-Karten in einem Grid/LazyColumn. Ein
  Reviews-Badge dort würde jede Karte um ~40dp wachsen lassen und zusätzliche
  Hilt-VMs pro Karte instanzieren (Lazy-Listen-Scrolling-Druck).
- `MealDetailSheet.kt:54` ist das Bottom-Sheet, das nach Antippen einer Karte aufgeht.
  Dort gibt es viel Platz, der User signalisiert bereits Interesse am Gericht, und
  der Badge erscheint direkt unter Hero-Preisen (= "unter Name/Preis" wie im Brief).
- Brief sagt explizit: "if unsure, add to MealDetailSheet (heavier real estate, less
  visual clutter on grids)" — exakt der Default.

Konkret eingefügt zwischen `SecondaryPriceLine` und der ersten `SectionDivider`
(Eigenschaften-Block). `ReviewBottomSheet` wird als Geschwister-Element des
ModalBottomSheets (außerhalb des inneren `Column`) gerendert, sodass es das
Detail-Sheet überlagern kann.

---

#### Build Outcome

```
BUILD SUCCESSFUL in 8s
43 actionable tasks: 11 executed, 32 up-to-date
```

`./gradlew :app:assembleDebug` → success, keine Warnings im Kotlin-Compile.

---

#### Self-Review

**(a) parseNutrition handles null + malformed JSON gracefully:**
- `if (jsonStr.isNullOrBlank()) return null` → null- und leer-String-Eingaben werden ohne
  Exception abgefangen.
- Gesamter Body in `runCatching { ... }.getOrNull()` gewickelt → kaputtes JSON
  (`SerializationException`, `IllegalArgumentException`, `ClassCastException` z.B.
  wenn `per_100_grams` ein Array statt Objekt ist) liefert `null` statt zu crashen.
- Wenn alle 5 Nährwert-Felder am Ende `null` sind, gibt der `takeIf`-Block ebenfalls
  `null` zurück (saubere Trennung von "kein Datenfeld" vs. "Datenfeld leer").

**(b) Self-Trust-Hack TODO + phase4-Referenz:**
- `ReviewViewModel.submit` enthält direkt vor dem `if (keys.getOrNull() == null)`-Block
  den Kommentar `// TODO(phase4): durch LSF-Onboarding ersetzen` plus zweizeilige
  Erklärung. Wird beim Phase-4-LSF-Onboarding (Task 19) ersetzt.

**(c) ReviewViewModel Konstruktor-Änderung:**
- Konstruktor erweitert um `private val keys: MyKeyManager, private val trust: TrustDao`.
- Alle Call-Sites von `ReviewViewModel` laufen über Hilt (`hiltViewModel()` in
  `ReviewBottomSheet.kt:36`). `MyKeyManager` und `TrustDao` sind bereits in
  Hilt-Modules registriert (Tasks 5/8/11) — Hilt wired automatisch. Keine manuelle
  Instanzierung im Code. **Kein Call-Site bricht.**
- Build-Test bestätigt: KSP/Hilt-Codegen erfolgreich.

---

#### Konkretes Verhalten Ende-zu-Ende

1. User tippt auf Meal-Card → `MealDetailSheet` öffnet sich.
2. Unter Studi-Preis/Kalorien-Hero erscheint `ReviewBadge` als `Surface` mit
   `semantics.surfaceAlt`-Background.
3. Bei 0 Reviews: "Noch keine Bewertungen — sei der erste!"
4. User klickt "▾" → 4 Dimension-Stats (oder "Noch keine Detail-Bewertungen.") +
   "Bewerten ▸"-Button erscheinen.
5. Klick auf "Bewerten ▸" → `ReviewBottomSheet` öffnet sich (überlagert das
   Detail-Sheet).
6. User vergibt Sterne + Wieder-Bestellen → Senden.
7. Bei erstem Submit ohne Key: Self-Trust-Hack erzeugt still einen Ed25519-Key +
   schreibt einen `TrustEntity(source="local-dev", depth=0)`.
8. Repository signiert das ReviewEvent, persistiert in `review_events`, packt das
   Outbox-Event (Task 7/10) — `ReviewBottomSheet` schließt sich via `LaunchedEffect`,
   Badge re-collected den `aggregateFor`-Flow und zeigt `★ X.X (n=1) · 👍 …%`.

---

#### Concerns

- **Manueller Test:** Habe nicht auf Emulator/Device installiert (Task-Brief verlangt
  das in Step 4) — Build-Erfolg reicht laut Task-12-Brief-Variante im Prompt aus, aber
  empfehle eine smoke-test-run vor dem Merge.
- **Unverbundene Datei-Drift:** Im Working-Tree existieren un-staged Änderungen aus
  parallelen Tasks (learnweb, calendar, search). Diese wurden bewusst NICHT in den
  Task-12-Commit aufgenommen. Kjell sollte prüfen, ob die zu einer separaten Branch
  gehören.
- **`ReviewBadgeViewModel` per `hiltViewModel()` in jedem Detail-Sheet:** Nur eine
  Instanz pro Sheet, kein Performance-Issue. Sollte später ein Reviews-Aggregate auf
  jedem MealCard nötig werden (z.B. inline "★ 4.0" in der Card-Header), bräuchte es
  einen anderen Architektur-Schritt (z.B. State-Hoisting in MensaViewModel oder ein
  Map-State pro hash → aggregate, das in der MensaScreen-Liste vor-resolved wird).
  Nicht für diesen Task.
- **`ReviewBottomSheet` rendert WÄHREND `MealDetailSheet` offen ist** → zwei
  übereinanderliegende ModalBottomSheets. Material 3 sollte das stackbar machen, aber
  Smoke-Test bestätigen.
