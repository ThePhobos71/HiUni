# 01 — Project Description

> Erweiterte Variante des One-Pagers (abgegeben 31.05.2026 als „HiUni — Projekt-Konzept", Autoren Kjell Karstens & Johann Brosthaus). Diese Sektion erklärt, **was** wir bauen und **warum**, und stellt den Kontext gegenüber dem One-Pager etwas breiter dar.

## Problem

Studierende an der Universität Hildesheim haben Zugang zu vielen digital unterstützten Diensten — Mensaplan, LSF-Notenübersicht, Lernplattform-Abgaben, Bibliotheks-Raumbuchung, Hochschulsport, Unikino, Mail, Stundenplan. Die meisten dieser Dienste sind über **getrennte Webseiten** erreichbar, mit **keiner zentralen Übersicht** und ohne universelles Login-Konzept. In der Praxis bedeutet das: Wer mehrfach am Tag die Mensa, ausstehende Abgaben oder Mails checken will, muss sich für jeden Dienst neu anmelden und die jeweilige Webseite besuchen — auch auf dem Smartphone, das die Webseiten meist nicht angemessen unterstützt.

## Motivation

Wir wollen diese Dienste **übersichtlich und komfortabel** an einem Ort nutzen — und das auch anderen Studierenden ermöglichen. Mehrmals täglich neu anmelden, weil man E-Mails prüfen oder ausstehende Abgaben checken möchte, ist nervig und zeitraubend. Es gibt vergleichbare Apps (z.B. **Studo**), die einige dieser Features anbieten — aber nicht mit der **Tiefe und Spezifität für Uni Hildesheim**, die wir uns wünschen. Dazu kommt die Lernperspektive: Wir möchten an einem realen, eigenen Problem die Kursinhalte des MSE-Moduls praktisch anwenden — saubere Android-Architektur, Compose, Lifecycle-Management, Persistenz und Testing.

## Ziel

Wir entwickeln eine native Android-App, die Studierende der Uni Hildesheim in ihrem **Studienalltag** unterstützt. Konkret bündelt die App die folgenden Dienste in einer einheitlichen Oberfläche mit Tagesübersicht, Kalender-Integration und Push-Benachrichtigungen:

- **Mailservice** (IMAP-basiert, eigene Uni-Credentials)
- **LSF-Notenübersicht** (Scraper, da kein öffentliches API)
- **Übersicht ausstehender Abgaben** (Learnweb / Moodle via CAS-SSO)
- **Mensaplan** mit Bewertungs-Funktion (STW-ON-API + P2P-Reviews als Bonus-Feature)
- **Unikino-Programm** (unifilm.de-Scraper)
- **Gruppenraumbuchung Bib** (ubwww-Scraper für Verfügbarkeit, Buchung via Intent zur Bib-Website)
- **Buchung von Sportangeboten** mit Motivations­unterstützung (HSP-Scraper)
- **ToDo-Liste** (lokal, mit Kalender-Verknüpfung)
- *(optional) Karte des Uni-Campus*

Outcome-Frame: Eine Studentin der Uni Hildesheim soll in **unter 10 Sekunden nach App-Start** wissen, was sie heute zu tun hat, was es in der Mensa gibt, welche Mails ungelesen sind und welche Abgaben anstehen — ohne sich irgendwo neu anmelden zu müssen.

## Zielgruppe

- **Primär:** Studierende der Universität Hildesheim, alle Semester und Studiengänge
- **Sekundär:** Mitarbeitende mit Lehraufträgen, die mensen oder Mail-Inbox checken (Funktionen sind nutzbar, aber nicht der Designfokus)
- **Nicht-Zielgruppe:** Studierende anderer Hochschulen (Scraper, Endpoints und Credentials sind Uni-Hi-spezifisch); Smartphone-iOS-User (native Android-App)

## Verhältnis zu HiUni v1

Wir haben **HiUni v1** im Vorjahr im MSE-Kurs eingereicht — damals in einer überlappenden Konstellation (Kjell + Johann + ein dritter Kommilitone, der dieses Jahr nicht mehr im Modul ist). Wie alle Teams in diesem Kurs-Jahrgang haben wir mit Hesenius' Einverständnis die App **von Grund auf neu gebaut** — v2 ist nicht ein Refactor von v1, sondern eine komplette Neuentwicklung mit den Lessons aus v1 als Eingangsbedingung.

**Was sich von v1 nach v2 verändert hat:**

- **Architektur:** v1 war monolithisch, v2 ist Feature-First mit ADR-getragenen Modulgrenzen.
- **Scope:** v1 hatte einen kleineren Feature-Umfang. v2 schaffte das ursprüngliche Set (Home / Calendar / Mensa / Movies / Bib / Email) plus Reviews + Federation + Sport + Klausuren + Lerngruppen + Tablet-Layouts.
- **Tablet-Support:** in v1 nicht vorhanden, in v2 mit drei adaptiven Layouts (Bottom-Nav / Rail / Drawer) realisiert.
- **AI-Workflow:** v1 wurde ohne AI-Assistenz geschrieben, v2 nutzt Claude Code als Generator/Reviewer (siehe [04-ai-workflow.md](04-ai-workflow.md)).
- **Was bereits in v1 da war:** MVVM, Room.
- **Workarounds bekannt:** Lessons wie Session-Fixation im ubwww-Scraper (`docs/UBWWW_BUG_SESSION_FIXATION.md`) und STW-API-Schema-Drift sind dokumentiert in v2 eingegangen.

_TODO Kjell/Johann_: Weitere konkrete Unterschiede ergänzen, die uns einfallen — z.B. UI-Stack (XML vs Compose? Oder schon in v1 Compose?), DI (Hilt vs. manuell?), Test-Coverage, Persistenz-Migrations.

Engineering-Log-Sektion [Phase 0](03-engineering-log.md#phase-0--rebuild-aus-v1-vor-23052026) hat die Details.

## Lösungsansatz auf einer Ebene

Native Android-App in Kotlin + Jetpack Compose mit Feature-First-Architektur. Die App aggregiert öffentlich zugängliche Datenquellen (STW-ON-API für Mensa, ubwww-Scraper für Bibliothek, unifilm.de-Scraper für Kino, Learnweb-CAS-SSO für Kurse, IMAP für Mail, LSF-Scraper für Noten) in eine einheitliche Oberfläche mit Tagesübersicht, Kalender-Integration und Push-Benachrichtigungen.

Tech-Highlights:

- **Compose-only, Single-Activity, MVVM, Hilt-DI, Room-Persistenz** (mit sqlcipher-Verschlüsselung)
- **Drei adaptive Layouts** (Phone Bottom-Nav / Tablet Rail / Tablet Drawer) — `LocalWindowSizeClass`-Switch
- **Lokal-first:** Alles funktioniert ohne Backend, Sync nur opt-in pro Feature
- **P2P-Reviews** (Mensa) als optionales Bonus-Feature mit Ed25519-Signaturen + Master-WoT — siehe [02-architecture-overview.md](02-architecture-overview.md) und Relay-Federation-Spec
- **Background-Sync** über WorkManager (Mail-Refresh, Mensa-Update, Notifications)
- **Strukturiertes Logging** mit Timber (Production-Builds deaktiviert), LeakCanary nur in Debug

Vollständige Library-Liste mit Begründung pro Library: [`HIUNI_LIBRARIES.md`](../../HIUNI_LIBRARIES.md) (Repo-Root) und [ADR-0005](../adr/0005-library-strategy.md).

## Was diese App NICHT ist

- **Kein Ersatz für offizielle Uni-Tools** (LSF, Stud.IP, Learnweb) — wo möglich aggregieren wir öffentliche Inhalte über Scraper / Login-Sessions, wo Booking nötig ist (Bib-Räume, Hochschulsport) öffnen wir die offizielle Webseite via Intent.
- **Keine Crowd-Bewertungs-Plattform** — Reviews sind Mensa-only und Trust-basiert (Master-WoT mit Ed25519-Signaturen). Kein offenes Schreibrecht für Anonyme, keine Moderation, keine Spam-Resistenz für globale Skalierung.
- **Keine Verwaltungs-App der Uni-IT** — wir sind ein privates Studierenden-Projekt im Rahmen des MSE-Kursmoduls, nicht offiziell mit der Uni-Hi-IT abgestimmt.
- **Keine Cross-Platform-App** — bewusst native Android, kein Flutter / kein React Native (Kursvorgabe + Design-Entscheidung).
- **Kein Backend-Web-Service** — `hiuni-relay` (Ktor-Mini-Server) ist nur für das optionale P2P-Reviews-Feature, dient als Relay zwischen Devices und ist nicht für klassische Backend-Logik.

## Abgrenzung zum One-Pager

Diese Sektion erweitert den One-Pager: Was im One-Pager auf einer Seite stand, ist hier mit Hesenius-Kontext (v1-Beziehung), technischen Constraints (NICHT-Liste, Lösungsansatz) und einem klareren Outcome-Frame versehen.
