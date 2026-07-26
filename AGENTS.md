# AGENTS.md

Guidance for AI agents (and humans) working in the **Trails** repository.

## Project overview

Trails is a device-tracking / "find my device" system. It lets users locate,
ring and share the location of their devices across an Android/iOS app and a
web app, backed by a self-hostable Ktor server.

The repository is a **Gradle multi-module Kotlin project** combined with a
separate SvelteKit web frontend. The Gradle modules are declared in
[settings.gradle.kts](settings.gradle.kts):

| Module         | Path                          | Description                                                                                            |
|----------------|-------------------------------|--------------------------------------------------------------------------------------------------------|
| `:server`      | [`server/`](server)           | Ktor backend (JVM). REST + WebSocket/SSE API, SQLite persistence, Koin DI, Authentikt auth.            |
| `:shared`      | [`shared/`](shared)           | Kotlin Multiplatform module with the shared API contract (DTOs / entities) between server and app.     |
| `:app:shared`  | [`app/shared/`](app/shared)   | Compose Multiplatform app (common UI + platform code for Android/iOS), Room DB, Koin DI.               |
| `:app:android` | [`app/android/`](app/android) | Android application entry point (activities, manifest, resources).                                     |
| `app/ios`      | [`app/ios/`](app/ios)         | iOS application entry point (Xcode project, SwiftUI shell).                                            |
| `web/`         | [`web/`](web)                 | SvelteKit web app (Svelte 5, TypeScript, Tailwind, shadcn-svelte, Mapbox GL). **Not** a Gradle module. |

Shared root package for all Kotlin code: `es.jvbabi.trails`.

### Server layout ([server/src/main/kotlin/es/jvbabi/trails](server/src/main/kotlin/es/jvbabi/trails))

- `Application.kt` / `Main.kt` — application bootstrap; `rootModule` installs all
  Ktor plugins in order and finishes with `installRouting()`.
- `api/` — Ktor plugin installation (`installContentNegotiation`,
  `installAuthentication`, `installStatusPages`, WebSocket, SSE, …).
- `auth/` — Authentikt integration and session/device-selection auth.
- `config/` — application configuration model.
- `data/` — repositories and external services (e.g. Nominatim reverse geocoding).
- `database/` — persistence entities (`Device`, `User`, `Share`, `ActiveShare`, …),
  `DatabaseManager`, and `mapper/` that maps DB entities to shared DTOs.
- `di/` — Koin module setup (`installKoin`).
- `routes/` — HTTP/WebSocket route handlers. See coding rules below.

### Shared API contract ([shared/src/commonMain/kotlin/es/jvbabi/trails](shared/src/commonMain/kotlin/es/jvbabi/trails))

`@Serializable` DTOs shared across server, app and (conceptually) the web
client. Organised under `api/v1/…` (entities, request/response bodies) and
`shared/dto/…`. Keep this module free of platform- or server-specific code.

### App layout ([app/shared/src/commonMain/kotlin/es/jvbabi/trails](app/shared/src/commonMain/kotlin/es/jvbabi/trails))

Clean-architecture-ish structure: `data/` (Room database, remote `TrailsApi`,
repository implementations), `domain/` (models, repository interfaces, use
cases), `page/` (Compose screens + view models), `ui/` (shared components,
theme). Platform-specific code lives in `androidMain/` / `iosMain/`.

## Build & run

```bash
# Server (JVM)
./gradlew :server:run

# Android app
./gradlew :app:android:assembleDebug

# Web app
cd web && bun install && bun run dev
cd web && bun run check   # svelte-check type checking
```

iOS is built from Xcode via [app/ios](app/ios).

> **Agents: never start or run anything yourself** (no `./gradlew run`, no
> `bun run dev`, no server, app, emulator or dev-server launches). The user
> keeps the relevant services running. If something you need is offline or not
> reachable, **ask the user to start it** instead of starting it yourself.

## Coding rules

These rules are mandatory. Prefer following existing patterns in neighbouring
files over inventing new ones.

### Ktor routing

- **All routing structure lives in
  [installRouting.kt](server/src/main/kotlin/es/jvbabi/trails/routes/installRouting.kt).**
  This is the *only* place `route(...) { }` may be used, and every route path
  must be declared there explicitly. This keeps the full API surface visible in
  a single file.
- Individual handler files (e.g.
  [`routes/devices/item/getItem.kt`](server/src/main/kotlin/es/jvbabi/trails/routes/devices/item/getItem.kt))
  define the endpoint logic as extension functions (`get`/`post`/`webSocket`/…
  or plain `suspend fun ApplicationCall`), and are *wired in* from
  `installRouting.kt`. Do not add `route(...)` blocks inside handler files.

### Serialization

- Every `@Serializable` class must annotate **all** properties with
  `@SerialName`, giving the explicit wire name. Never rely on the implicit
  Kotlin property name. Example:

  ```kotlin
  @Serializable
  data class Device(
      @SerialName("id") val id: Uuid,
      @SerialName("friendly_name") val friendlyName: String,
      @SerialName("owner_id") val ownerId: Uuid,
  )
  ```

  Wire names use `snake_case`; Kotlin properties use `camelCase`.

### Comments & documentation

- All comments must be written in **English**.
- Add KDoc / Javadoc-style documentation comments where they add value
  (public APIs, non-obvious behaviour, invariants). Don't document the obvious.

### Web tooling

- Always use **bun** for the Svelte/`web` project (`bun install`, `bun run …`,
  `bunx …`). Never use `npm`, `pnpm` or `yarn`.

## Notes

- The repo's top-level `README.md` is the default Kotlin Multiplatform template
  and does not reflect the current module layout — trust this file instead.
- Server runtime data (SQLite DB, JWT secret, config) lives under
  `server/data/` and is environment-specific — do not commit changes to it.
