# Nexus Media Tracker

A unified media tracker: one dashboard with switchable modules for games, films and TV,
anime and manga, and books — replacing the need to juggle a separate service per medium.

Built as a modular monolith. Tracking, rating and activity logic lives once in a shared
core; each medium contributes only an external-API adapter, its item metadata and a
progress shape. External APIs are cached globally, so calls scale with the number of
distinct titles tracked rather than with the number of users.

## Project status

**Work in progress — phase 5 of 8.** Built one module at a time, games first, so the
shared core is proven by a real vertical slice before a second medium is added.

| | |
|---|---|
| ✅ **Phase 0** | Scaffold, JWT auth, PWA shell, local Postgres |
| ✅ **Phase 1** | Games: IGDB search, track with a status, dashboard |
| ✅ **Phase 2** | Steam import — connect an account, pull a library with playtimes |
| ✅ **Phase 2b** | Containerized and deployed |
| ✅ **Phase 3** | Activity feed, ratings, progress editing, reviews, Steam achievements |
| ✅ **Phase 4** | Cache staleness and refresh |
| ✅ **Phase 5** | Anime and manga — AniList as canonical, AniList and MAL as library feeds |
| ✅ **Phase 6–7** | Films and TV, books |
| ⬜ **Phase 8** | Enable and disable modules per user |

What that means today: the games module is complete. You can search IGDB, track a title,
edit status, rating, progress, dates and notes, write a review, and read back an activity
feed of every change. Connecting Steam pulls your library with playtimes and reports what
it could not match; a second sync walks the library for per-game achievement progress in
the background. Cached items refresh themselves on read once their state-keyed TTL is up.

The shell is built around the module switcher the later phases need: modules come from a
registry, and each contributes its own status vocabulary — so anime will say "Watching"
where games say "Playing". Unbuilt modules are listed as such rather than hidden, and light
and dark themes both follow the system or an explicit choice.

## Stack

Spring Boot 4 (Java 21) · React 19 + Vite (PWA) · PostgreSQL 17 · Flyway · JWT auth

Requires an IGDB client id and secret, obtained through a Twitch developer application.
Game search is disabled without them; the rest of the app still runs. A Steam web API key
enables the library import and achievement sync, and is optional in the same way.

## Running locally

Requires Docker and a JDK 21. Node 20+ for the frontend.

The whole application, from a fresh clone:

```bash
cp .env.example .env      # then fill in the blanks
docker compose up         # http://localhost:8080
```

Or with the frontend on Vite's dev server, for hot reload:

```bash
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run      # :8080, Flyway migrates on boot
cd frontend && npm install && npm run dev # :5173, proxies /api to the backend
```

One image serves the API and the built frontend from the same origin. Splitting them
would make the refresh cookie cross-site, and `SameSite=Strict` means the browser would
withhold it — so every session would end at the first reload.

`SPRING_PROFILES_ACTIVE=dev` relaxes the cookie Secure flag so auth works over plain
http on localhost. Production runs the `prod` profile, where it stays on.

`NEXUS_ENCRYPTION_KEY` encrypts stored OAuth tokens at rest. It must stay stable: change
it and every token already stored becomes unreadable.

## Tests

```bash
cd backend && ./mvnw test   # spins up Postgres via Testcontainers
cd frontend && npm run lint && npm run build
```

The suite covers the two claims the architecture rests on: a second user tracking an
already-cached title costs no additional API call, and no user can reach another user's
entry by any route.
