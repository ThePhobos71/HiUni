FROM python:3.12-slim

WORKDIR /app

RUN groupadd --gid 1000 app && useradd --uid 1000 --gid app --shell /bin/false app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

RUN mkdir -p /data && chown -R app:app /data /app

USER app

EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
