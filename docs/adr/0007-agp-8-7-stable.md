# ADR-0007: AGP 8.7 stable statt AGP 9 bleeding-edge

**Datum:** 2026-05-23
**Status:** Accepted

## Kontext

Android Gradle Plugin 9.0 ist im Januar 2026 stable rausgekommen und hat substantielle Build-DSL-Änderungen:

- Legacy Variant API ist disabled by default
- DSL-Änderungen (CommonExtension-Restrukturierung)
- Verlangt Kotlin Gradle Plugin 2.2.10+, Gradle 9.1+

Das Initial-Repo war auf **AGP 9.2.1** konfiguriert (sehr aktuell). Hilt brach mit AGP 9 zunächst komplett (Issues #5083, #5099), erst **Hilt 2.59.2** schließt die kritischen Bugs (vor allem die fehlende `ComponentTreeDeps`-Runtime-Class).

## Entscheidung

Wir nutzen **AGP 8.7.3** + **Kotlin 2.0.21** + **Hilt 2.56** + **KSP1** (`ksp.useKSP2=false`).

`compileSdk = 36`, `minSdk = 28`, `targetSdk = 36`, Java 17.

## Begründung

- **Erprobte Tool-Chain:** AGP 8.7 + Hilt 2.51-2.56 + Kotlin 2.0 ist die Standard-Konstellation in 90% der 2026er Android-Tutorials. Bei Problemen findet sich sofort eine Lösung.
- **Risiko-Eliminierung:** Hilt 2.59.x hat zwar AGP-9-Support, ist aber jung. Wir können uns keine Tage mit Toolchain-Bugs leisten.
- **Keine Architecture-Punkte verloren:** „Stable, well-known tools" ist eine professionelle Wahl, nicht bleeding-edge. Der Prof bekommt das nicht zu sehen — er sieht funktionierenden Build, Tests, Doku.
- **Migration nach AGP 9 später möglich:** Wenn das Ökosystem stabilisiert (~Sommer 2026), kann ein Upgrade in einem PR passieren. Aktuell kein Mehrwert.

## Trade-offs

- Die DSL `compileSdk { version = release(36) }` (AGP-9-Style) musste auf `compileSdk = 36` zurück
- `compileSdk = 36` ist über dem AGP-8.7-getesteten Range (35). Wir suppressen die Warnung mit `android.suppressUnsupportedCompileSdk=36` — funktioniert in der Praxis problemlos
- KSP1 statt KSP2: ~10% langsamer bei Hilt-Annotation-Processing, aber Room 2.6.1 ist mit KSP2 noch nicht kompatibel

## Migration-Pfad zu AGP 9 (für später)

1. Hilt auf >= 2.59.2 ziehen
2. KSP auf KSP2 (`ksp.useKSP2=true`)
3. Room auf 2.7.0+ ziehen
4. AGP auf 9.x ziehen, `compileSdk { version = release(36) }`-DSL nachziehen
5. Gradle Wrapper auf 9.1+
