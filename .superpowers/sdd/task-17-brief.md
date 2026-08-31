### Task 17: Dockerfile + docker-compose + Caddy

**Files:**
- Create: `hiuni-relay/Dockerfile`
- Create: `hiuni-relay/docker-compose.yml`
- Create: `hiuni-relay/Caddyfile`
- Create: `hiuni-relay/.env.example`

**Interfaces:**
- Produces: Container, `docker compose up -d` startet alles, HTTPS via Caddy ist konfiguriert.

- [ ] **Step 1:** `Dockerfile`:

```dockerfile
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :hiuni-relay:installDist --no-daemon

FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=build /app/hiuni-relay/build/install/hiuni-relay /app
WORKDIR /app
ENV DB_PATH=/data/relay.db \
    HMAC_SECRET="" \
    MASTER_KEY_B64=""
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["bin/hiuni-relay"]
```

- [ ] **Step 2:** `docker-compose.yml`:

```yaml
services:
  relay:
    build:
      context: ../
      dockerfile: hiuni-relay/Dockerfile
    environment:
      DB_PATH: /data/relay.db
      HMAC_SECRET: ${HMAC_SECRET}
      MASTER_KEY_B64: ${MASTER_KEY_B64}
    volumes:
      - relay-data:/data
    expose: ["8080"]
    restart: unless-stopped

  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on: [relay]
    restart: unless-stopped

volumes:
  relay-data:
  caddy-data:
  caddy-config:
```

- [ ] **Step 3:** `Caddyfile`:

```
{$RELAY_DOMAIN:relay.localhost} {
    reverse_proxy relay:8080
}
```

- [ ] **Step 4:** `.env.example`:

```
HMAC_SECRET=change-me-to-32-bytes-random
MASTER_KEY_B64=base64-encoded-ed25519-private-key
RELAY_DOMAIN=relay.example.com
```

- [ ] **Step 5:** Lokal smoketesten:

```bash
cd hiuni-relay
cp .env.example .env
# In .env: HMAC_SECRET füllen, MASTER_KEY_B64 leer für jetzt
docker compose build
docker compose up -d
curl http://relay.localhost/health
docker compose down
```

- [ ] **Step 6:** Commit:

```bash
git add hiuni-relay/Dockerfile hiuni-relay/docker-compose.yml hiuni-relay/Caddyfile hiuni-relay/.env.example
git commit -m "feat(relay): Dockerfile + Caddy-Sidecar + compose"
```

