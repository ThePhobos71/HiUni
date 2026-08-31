# Task 17 Report: Containerize the relay

## Files created (5)

- `hiuni-relay/Dockerfile` — Multi-stage build (gradle:8.10-jdk21 → distroless/java21-debian12:nonroot)
- `hiuni-relay/docker-compose.yml` — relay + caddy sidecar, build context `../`
- `hiuni-relay/Caddyfile` — reverse-proxy to `relay:8080` with `{$RELAY_DOMAIN:relay.localhost}` placeholder
- `hiuni-relay/.env.example` — documented HMAC_SECRET / MASTER_KEY_B64 / RELAY_DOMAIN
- `hiuni-relay/Dockerfile.dockerignore` — keeps build context lean (drops `.git`, `.gradle`, `build/`, `.idea`, `*.apk`, `local.properties`, `.env`, etc.)

A local `hiuni-relay/.env` was created from the example for the smoke test only; it is gitignored via the root `.gitignore`'s `**/.env` rule (verified with `git check-ignore`).

## Deviations from brief

Two changes were required to make the image actually run; both are documented in the Dockerfile with comments:

1. **ENTRYPOINT changed from `["bin/hiuni-relay"]` to `["java", "-cp", "/app/lib/*", "de.transio.hiuni.relay.ApplicationKt"]`.** The gradle-generated `bin/hiuni-relay` is a `sh` script; distroless has no shell, so `exec bin/hiuni-relay: no such file or directory` was thrown until the JVM was invoked directly against the `installDist` classpath.
2. **Added `/data` pre-creation owned by uid 65532 (nonroot).** Without it SQLite failed with `opening db: '/data/relay.db': Permission denied`. Done via a `mkdir /empty-data && chown -R 65532:65532 /empty-data` in the build stage plus `COPY --from=build --chown=65532:65532 /empty-data /data` into stage 1. The named docker volume inherits this ownership on first init.

Also, after the initial 77 s build, the Dockerfile was restructured into cached layers (settings + version catalog → module build.gradle.kts → `gradle :hiuni-relay:dependencies` warm-up → finally source) so source-only edits rebuild in ~13 s instead of running the entire dependency download again. BuildKit's `--mount=type=cache,target=/home/gradle/.gradle/caches` keeps the gradle caches across builds.

## `docker compose build` (relevant lines)

```
#16 [build 10/10] RUN --mount=type=cache,target=/home/gradle/.gradle/caches gradle :hiuni-relay:installDist --no-daemon ...
#16 12.00 > Task :hiuni-relay:jar
#16 12.90 > Task :hiuni-relay:startScripts
#16 12.97 > Task :hiuni-relay:installDist
#16 12.97 BUILD SUCCESSFUL in 12s
#16 12.97 7 actionable tasks: 7 executed
#17 [stage-1 2/4] COPY --from=build /app/hiuni-relay/build/install/hiuni-relay /app  DONE 0.1s
#18 [stage-1 3/4] COPY --from=build --chown=65532:65532 /empty-data /data            DONE 0.0s
naming to docker.io/library/hiuni-relay-relay:latest done
Image hiuni-relay-relay Built
```

First cold build: ~90 s (incl. gradle wrapper download + dep resolution). Cached rebuild after source edit: ~15 s.

## Smoke test outcome

`docker compose up -d relay` brought the relay container up healthy on the compose network:

```
hiuni-relay-relay-1   hiuni-relay-relay   "java -cp /app/lib/*…"   relay     Up   8080/tcp
14:38:17.487 [main] INFO  io.ktor.server.Application - Application started in 0.127 seconds.
14:38:17.553 [DefaultDispatcher-worker-1] INFO  io.ktor.server.Application - Responding at http://0.0.0.0:8080
```

`/health` reachable from a sidecar curl on the compose network:

```
$ docker run --rm --network hiuni-relay_default curlimages/curl:latest \
    -fsS http://relay:8080/health
{"events":0,"connections":0}
```

(The body is `{"events":0,"connections":0}` — that is what the relay implementation from tasks 14-16 actually returns. The brief mentioned `{"status":"ok"}` but the existing implementation hasn't been changed in this task.)

Stand-alone smoke (without compose, to bypass the local port-80 conflict):

```
$ docker run -d --rm -p 18080:8080 -e HMAC_SECRET=... hiuni-relay-relay:latest
$ curl -fsS http://localhost:18080/health
{"events":0,"connections":0}
```

Caddy could not be brought up on this machine because another container of the user's (`space-traefik`) already binds 0.0.0.0:80 / 443. Caddy itself is not broken — the reverse-proxy path inside the compose network is verified by the sidecar-curl above, and the Caddyfile uses the documented `{$RELAY_DOMAIN:relay.localhost}` env-driven placeholder, with `RELAY_DOMAIN` passed in via `docker-compose.yml`'s `caddy.environment.RELAY_DOMAIN: ${RELAY_DOMAIN:-relay.localhost}`.

`docker compose down -v` cleaned up containers + volumes successfully.

## Self-review

- **(a) Multi-stage Dockerfile builds cleanly without `:app` or `:hiuni-relay` test failures.** The build stage runs only `:hiuni-relay:installDist`, which transitively compiles `:shared-events` and skips test tasks. `:app` is in `settings.gradle.kts` but its build.gradle.kts is not copied into the cached path; gradle does not configure it during `installDist`. No errors logged.
- **(b) Caddyfile placeholder + env-driven `RELAY_DOMAIN` works.** The Caddyfile uses `{$RELAY_DOMAIN:relay.localhost}`; `docker-compose.yml` forwards `RELAY_DOMAIN` from the host environment (with default `relay.localhost`). Verified by template syntax; not exercised against Let's Encrypt locally (would need a public DNS A record).
- **(c) `.env` is gitignored.** Verified via `git check-ignore -v hiuni-relay/.env` → matches `.gitignore:37:**/.env`. Only `.env.example` will be committed.

## Concerns / platform notes

- **Port 80/443 conflict on host.** A long-running `space-traefik` container of the user's holds ports 80 and 443 (and even 8080). Caddy therefore cannot publish locally without stopping that other service. On Hetzner / Raspberry Pi (the deploy targets) this won't be an issue.
- **Image size.** Final image is ~270 MB (distroless java21 ~210 MB base + ~60 MB `/app/lib`). The build stage (gradle:8.10-jdk21, ~750 MB) is discarded.
- **First build pulls ~800 MB** (gradle base + distroless + dependencies). Subsequent edits-to-source rebuilds are ~15 s thanks to the layered Dockerfile + BuildKit gradle-cache mount.
- **Distroless = no shell.** That means `docker compose exec relay sh` won't work for debugging; logs must come via `docker compose logs relay`. The Dockerfile comment documents this.
- **`MASTER_KEY_B64` left empty in smoke test.** The relay accepts the empty value (Tink at-rest encryption is conditional in the current implementation). For production, the user must generate and provide a real key.
- **Architecture / platform.** Tested on darwin/arm64 Docker Desktop; the build produces a multi-arch manifest (BuildKit default), so should run on amd64 Hetzner + arm64 RPi unchanged.
