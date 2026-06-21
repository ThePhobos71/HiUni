# ADR-0003: Hilt für Dependency Injection

**Datum:** 2026-05-23
**Status:** Accepted

## Kontext

In HiUni v1 wurde manuelles DI über die `Application`-Klasse mit `by lazy { ... }` betrieben. Das war kompakt, hatte aber Smells:

- Repositories wurden teilweise mit dem Application-Context als Konstruktor-Argument gebaut
- Easter-Egg-State (`mutableStateOf` für Theme-Switching) hing am Application-Singleton
- Test-Doubles waren nur durch globale Override-Mechanismen einspielbar

## Entscheidung

Wir nutzen **Hilt** (Dagger) als DI-Framework.

- `@HiltAndroidApp` auf `HiUniApplication`
- `@AndroidEntryPoint` auf `MainActivity`
- `@HiltViewModel` auf alle ViewModels, Konstruktor-Injektion über `@Inject`
- App-wide Module in `di/`: `DatabaseModule`, `NetworkModule`, `DataStoreModule`
- Feature-interne Bindings (Repository-Interface → Impl) in `feature/<name>/data/<Name>RepositoryModule.kt`

## Begründung

- Compile-Time-Validierung der Dependency-Graphen (keine Runtime-Surprises)
- Standard im Android-Ökosystem 2024+, gute Tutorial-Coverage
- Test-Doubles via `@TestInstallIn` und Hilt-Test-Rule sind sauber
- Komplexere Singletons (DataStore + Coroutine-Scope, OkHttp + Cache-Dir) lassen sich klar in Modulen kapseln

## Trade-offs

- Build-Zeit-Hit durch KSP-Annotation-Processing (~2-4s pro Build mehr)
- KSP-Versions-Kompatibilität mit Room ist tückisch — wir nutzen KSP1-Mode (`ksp.useKSP2=false`) bis Room 2.7+ stable im Stack ist
- Hilt erfordert mehr Boilerplate als Koin, gibt dafür Compile-Time-Safety
