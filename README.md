# Nexus Media Tracker

One dashboard for the games, films, shows, anime and books you track, replacing the
need to juggle a separate service per medium.

Built as a modular monolith: tracking, rating and activity logic lives once in a shared
core, and each medium contributes only an external-API adapter, its item metadata and a
progress shape. External APIs are cached globally, so lookups scale with distinct titles
rather than with users.

## Stack

Spring Boot (Java 21) · React + Vite (PWA) · PostgreSQL · Flyway · JWT auth

## Running locally

Requires Docker and a JDK 21. Node 20+ for the frontend.

```bash
cp .env.example .env      # then fill in the blanks
docker compose up -d      # Postgres on :5432

cd backend && ./mvnw spring-boot:run      # :8080, Flyway migrates on boot
cd frontend && npm install && npm run dev # :5173, proxies /api to the backend
```

`SPRING_PROFILES_ACTIVE=dev` relaxes the cookie Secure flag so auth works over plain
http on localhost. Production runs the `prod` profile, where it stays on.

## Tests

```bash
cd backend && ./mvnw test   # spins up Postgres via Testcontainers
cd frontend && npm run lint && npm run build
```
