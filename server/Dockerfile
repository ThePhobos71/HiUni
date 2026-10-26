FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    DB_PATH=/data/tokens.db

WORKDIR /app

# Fester Nicht-root-User (uid/gid 1000). Ein frisch angelegtes Named Volume
# (z.B. Coolify Persistent Volume auf /data) übernimmt beim ersten Mount die
# Besitzrechte des Image-Verzeichnisses, gehört also direkt diesem User.
RUN groupadd --gid 1000 app && useradd --uid 1000 --gid app --shell /bin/false app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

RUN mkdir -p /data && chown -R app:app /data /app

USER app

EXPOSE 8000

# Health-Check ohne curl/wget (nicht im slim-Image): reines Python-urllib.
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD ["python", "-c", "import sys, urllib.request; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/healthz', timeout=4).status == 200 else 1)"]

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
