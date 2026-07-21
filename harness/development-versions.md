# Canonical Development Tool Versions

> Single source of truth for the language / package manager / build tool versions
> used by this repository. Pin files at repo root (`.nvmrc`, `.java-version`) MUST
> match the values declared here.

## Why pin

Without pinned versions, a new contributor's local toolchain (e.g. Node 22 vs
Node 20, Java 21 vs Java 17) silently produces different `pnpm-lock.yaml`
resolution or `mvn` bytecode than CI. Pinning at repo root is the lowest-effort
intervention: IDEs and shell startup hooks (`fnm`, `nvm`, `jenv`, `sdkman`)
auto-switch when they see these files.

## Current canonical versions

| Tool        | Version | Pin file                  | CI config                          |
|-------------|---------|---------------------------|------------------------------------|
| Node.js     | 20      | `.nvmrc`                  | `.github/workflows/ci.yml` `node-version: "20"` |
| pnpm        | 9       | (Jenkinsfile `pnpm@9`)    | `.github/workflows/ci.yml` `pnpm/action-setup` `version: 9` |
| JDK         | 17      | `.java-version`           | `.github/workflows/ci.yml` `java-version: "17"` |
| Maven       | 3.9.x   | (NOT pinned yet — see TODO)| pom.xml `<java.version>17</java.version>` |
| PostgreSQL  | 16      | (compose only)            | `docker-compose.test.yml`           |
| Redis       | 7       | (compose only)            | `docker-compose.test.yml`           |

## How to keep these in sync

When bumping any version, update **all three** in lockstep:

1. The pin file at repo root (`.nvmrc` / `.java-version`).
2. The CI workflow file (`.github/workflows/ci.yml`).
3. This table.

CI governance does not currently enforce the table against the pin files;
that automation is a follow-up tracked under issue #165.

## Known TODO: Maven Wrapper

`mvnw` / `mvnw.cmd` / `.mvn/wrapper/maven-wrapper.jar` are **not yet** committed.
Until they are:

- Developers must install Maven 3.9.x locally and keep it on PATH.
- CI uses the `setup-java` action with Maven cache, but does not pin a specific
  Maven version beyond what `actions/setup-java` ships.
- The Jenkins host relies on the system Maven that the Jenkins image provides.

Adding the wrapper is a follow-up that must NOT change the resolved Maven
version (3.9.x) without going through the bump protocol above.

## Known TODO: pnpm `packageManager` field

We intentionally do **not** add `"packageManager": "pnpm@9.0.0"` to
`frontend/package.json` because:

- It requires Corepack on every developer machine.
- `pnpm/action-setup` in `.github/workflows/ci.yml` already pins pnpm 9
  explicitly, so CI is unaffected.
- Forcing Corepack on Windows + WSL + macOS contributors has historically
  caused more friction than benefit; we revisit once the Node Harness
  (issue #165) lands.

## Frontend Dockerfile

`frontend/Dockerfile` uses `node:20`. Bumping Node means updating the Dockerfile
in addition to the rows above.

## Backend Dockerfile (if present)

`backend/Dockerfile` should use `eclipse-temurin:17-jdk-jammy` or equivalent.
Verify against `.github/workflows/ci.yml` `distribution: temurin` + `java-version: 17`.