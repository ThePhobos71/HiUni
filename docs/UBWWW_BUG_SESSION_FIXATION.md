# ubwww Gruppenraumbuchung – Session-Fixation / Daily-Limit-Bypass

**Status:** Noch nicht an UB IT gemeldet
**Entdeckt:** 25.05.2026 beim Implementieren des Bib-Buchungsflows in UniHi
**System:** `https://ubwww.uni-hildesheim.de/gruppenraumbuchung/` (Lars-Heuer-Stack auf PHP)

## Kurzfassung

Das Buchungssystem regeneriert die `PHPSESSID` nach erfolgreichem CAS-Login,
aber die alte (anonyme) Pre-Auth-Session wird serverseitig **nicht zerstört**.
Buchungen mit der alten Session-ID werden vom Backend akzeptiert, sind aber an
keine User-Identity gebunden. Konsequenz:

1. **Daily-Limit (1 Buchung pro Tag pro User) lässt sich umgehen** — die Regel
   greift offenbar an der User-ID, nicht an der Session.
2. **„Verwaiste Buchungen"**: Slot ist serverseitig belegt, taucht aber bei
   keinem User unter „Meine Buchungen" auf. Nicht stornierbar durch normale
   User, weil das System keinen Owner findet.
3. **Verfügbarkeits-Sabotage** denkbar: Ein Angreifer kann pro CAS-Login einen
   anonymen Session-Cookie abgreifen und damit Räume blocken, ohne dass das
   Daily-Limit greift.

## Reproduktion

### Setup
- Beliebiger Uni-Hi Account
- HTTP-Client der Set-Cookie-Header lesen kann (curl mit `-D -`, oder Browser
  DevTools mit deaktivierter Cookie-Auto-Übernahme)

### Schritte

**1. CAS-Login durchlaufen mit Service `index.php?login`:**

```
GET https://ubwww.uni-hildesheim.de/gruppenraumbuchung/index.php?login&ticket=ST-...
```

Die Response (302) enthält zwei `Set-Cookie: PHPSESSID=...`-Header:

```
Set-Cookie: PHPSESSID=kvdq8cupqoh2qulrnjbvlcfptr; path=/      ← anonyme Pre-Auth-Session (26 chars)
Set-Cookie: PHPSESSID=540997e8765226de6995bf6d303dd070f5f60ddb35ab497486d9e53fed291e4c; path=/  ← User-Session nach Auth (64 chars)
```

Browser nehmen per RFC 6265 die **zweite** (last wins) — das ist die mit
gebundener User-Identity. Manuell aber kann man die **erste** behalten.

**2. Buchung mit der ersten (anonymen) PHPSESSID:**

```
GET https://ubwww.uni-hildesheim.de/gruppenraumbuchung/ajax_php/set_data.php
    ?action=book_room&value=20260610,850,900,101,
Cookie: PHPSESSID=kvdq8cupqoh2qulrnjbvlcfptr; gap_language=de
X-Requested-With: XMLHttpRequest
```

**Erwartet:** Reject mit „Bitte erst einloggen" oder gleichwertig.
**Tatsächlich:** Response Body `ok` — Buchung ist im Belegungs-Grid sichtbar.

**3. Belegungs-Grid prüfen (mit beliebigem Browser / Gerät / Anonym-Tab):**

```
GET https://ubwww.uni-hildesheim.de/gruppenraumbuchung/index.php
```

Der gebuchte Slot wird als rot (`#DF2E3B` = belegt) gerendert — sichtbar für
alle. Aber: kein zugehöriger Eintrag in `bookings.php` für den User, der den
ST-Ticket-Login durchgeführt hat.

## Beobachtung 2: `?login`-Query-Param als View-Switch

Eng verwandt, ebenfalls problematisch. `index.php?login` ist eigentlich die
CAS-Callback-URL (Service-Param), die nach erfolgreichem Ticket-Redeem den
PHPSESSID-Cookie setzt. ubwww re-used den Parameter aber als **persistenten
View-Switch**:

- `GET /index.php` mit auth-PHPSESSID → rendert **anonyme** Sicht (eigene
  Buchungen als `#DF2E3B`, kein `getConfirmationForm()`-onclick, kein
  `#999999`).
- `GET /index.php?login` mit selber PHPSESSID → rendert **authentifizierte**
  Sicht (eigene Buchungen als `#999999` mit `getConfirmationForm()` onclick).

Auswirkung: Drittanbieter-Clients (oder unsere App), die auf `index.php`
gehen, sehen ihre eigenen Buchungen nicht als „eigene". Workaround ist
trivial (`?login` immer dranhängen), aber sicherheitstechnisch problematisch:
die View-Auswahl hängt am Query-Parameter, nicht an der Session-Identität.
Ein vorsätzlicher Angreifer könnte das nutzen, um zu verschleiern, dass eine
Buchung dem User zugeordnet ist (`index.php` ohne `?login` zeigt sie ja
generisch rot, nicht als eigene).

**Empfehlung**: View-Rendering rein an `$_SESSION['user_id']` koppeln und
`?login` ausschließlich für den CAS-ST-Redeem-Pfad verwenden.

**4. Daily-Limit umgehen:**

Mit dem regulären (User-) Cookie eine zweite Buchung am gleichen Tag versuchen:

```
GET …?action=book_room&value=20260610,1000,1030,102,
Cookie: PHPSESSID=540997e8…
```

→ `Sie dürfen pro Tag nur 1 Buchung vornehmen.`

Aber: zweite Buchung mit weiterer anonymer Session (neuer CAS-Login, erste
PHPSESSID picken) → `ok`. Damit beliebig viele Buchungen am gleichen Tag.

## Vermutete Ursache

PHP-Standard-Pattern wäre:

```php
session_start();
authenticateViaCas();
session_regenerate_id(true);  // ← (true) = destroy old session
$_SESSION['user_id'] = $uid;
```

Bei ubwww wird die alte Session-ID offenbar nur **rotiert** (`session_regenerate_id(false)` oder gleichwertig), nicht **invalidiert**. Die alte Session bleibt im Session-Store gültig und kann weiter benutzt werden — ohne `user_id` im `$_SESSION`-Array, aber für den Booking-Endpoint reicht das offenbar.

Zusätzlich prüft `set_data.php?action=book_room` anscheinend nur, ob *irgendeine* gültige PHPSESSID präsentiert wird — nicht, ob die Session tatsächlich einen authentifizierten User trägt. Sonst hätte der Booking-Call mit der Anon-Session mindestens ein 403 / Redirect-zu-CAS produziert.

## Empfohlene Fixes (UB IT)

1. **`session_regenerate_id(true)`** in der Login-Routine — Pre-Auth-Session muss server-seitig gelöscht werden.
2. **Server-side Auth-Check** in `set_data.php` (und allen `action=*`-Endpoints):
   ```php
   if (empty($_SESSION['user_id'])) {
       http_response_code(401);
       exit('Nicht eingeloggt');
   }
   ```
3. **Daily-Limit-Check** sollte sowieso an `user_id` hängen (vermutlich tut er das schon — wird nur umgangen weil obige Checks fehlen).

## Bezug zu UniHi

Wir hatten den Bug in unserer App unbeabsichtigt repliziert:
- `BibSession.kt` hat aus den `Set-Cookie`-Headern `firstNotNullOfOrNull` statt `lastOrNull` genommen → wir landeten auf der anonymen Session
- Dadurch waren unsere Test-Buchungen sichtbar im Grid, aber nicht in „Meine Buchungen"
- Fix: `BibSession.kt:80` → `mapNotNull{...}.lastOrNull()`
- App nutzt den Bug nicht aktiv aus, sondern verhält sich nach dem Fix wie ein normaler Browser.

## Kontakt-Vorschlag UB

Mail an `bibliothek@uni-hildesheim.de` mit Verweis auf dieses Dokument,
außerdem Carbon Copy an die IT-Abteilung (RZ).
