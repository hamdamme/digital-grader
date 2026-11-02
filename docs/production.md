# Production deployment

The pilot deployment runs on a single virtual host with three processes:

1. Caddy serves the built frontend from `/var/www/grader` and proxies
   `/api/*` to the backend on `127.0.0.1:8080` (see `infra/caddy/Caddyfile`).
2. The backend jar runs as a systemd service and reads its environment from
   a private `.env` file.
3. PostgreSQL, RabbitMQ, and MinIO run as containers with all published ports
   bound to loopback only, so the only public entry point is Caddy on
   ports 80 and 443.

## Environment essentials

`APP_BASE_URL` is the public origin used in emailed links. Email
verification and password reset links break when it points anywhere other
than the served frontend.

`CORS_ALLOWED_ORIGINS` gates cross-origin API requests. Same-origin browser
traffic needs no entry, but email clients and preview tools sometimes open
links in a way that triggers a CORS preflight from the public origin. Listing
the public origin (for example `https://grader.hamdam.me`) keeps those
requests working.

`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `SMTP_FROM`
select the mail provider. With `SMTP_HOST` empty the backend logs message
text instead of sending, which suits local development only.

`AI_ENABLED`, `AI_PROVIDER`, `AI_BASE_URL`, `AI_MODEL`, and `AI_API_KEY`
configure the optional hint provider. The `openai` provider accepts any
OpenAI-compatible endpoint, including local models. Hints stay disabled
until a provider is configured.

## Deploying a release

Build the backend jar with `mvn -B -ntp -f backend/pom.xml verify`, build the
frontend with `npm ci && npm run build` inside `frontend/`, copy both to the
host, replace the web root contents with `frontend/dist`, and restart the
service. The service takes a few minutes to become healthy; poll
`/api/v1/health` until it returns `{"status":"UP"}`.
