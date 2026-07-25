# Nginx Security Upgrade Evidence

## Metadata

- Time: 2026-07-25 15:10 +08:00
- Environment: real-pre / production infrastructure
- Scope: infrastructure
- Branch: codex/183-talent-claim-oom-guard-release
- Commit: 259cfe9c
- Owned worktree: dirty from pre-existing user changes; this task adds only this report and its retro

## Security Review

Official Nginx advisories currently identify recent high-impact fixes for CVE-2026-42533 (map + regex buffer overflow), CVE-2026-60005 (slice-module memory disclosure), CVE-2026-56434 (SSI use-after-free), CVE-2026-42530 (HTTP/3 use-after-free), CVE-2026-42055 (proxy_v2/grpc buffer overflow), and CVE-2026-48142 (charset buffer overread). The official fixed-version line for the first three is stable 1.30.4+ / mainline 1.31.3+; official stable 1.30.4 was released on 2026-07-15.

Sources:

- https://nginx.org/en/security_advisories.html
- https://nginx.org/en/download.html
- https://nginx.org/2026.html

## Production Server

- SSH target: `saas` / `1.14.108.159` (production only)
- Before: `nginx/1.24.0`, Ubuntu package `1.24.0-2ubuntu7.9`
- Intermediate distro security update: `1.24.0-2ubuntu7.15`
- Final: official Nginx stable package `1.30.4-1~noble`, `nginx/1.30.4`
- Official signing key was imported and fingerprint output was checked; stable Ubuntu repository and pin were configured.
- Scope of package change: only `nginx` was upgraded to the official package; the Ubuntu `nginx-common` package was replaced as required by the official package.
- Rollback/config backup: `/etc/nginx.backup-pre-upgrade-20260725`
- The official package initially enabled its default `conf.d/default.conf`, so the prior business `nginx.conf` was restored from the backup and the default file was moved to `/etc/nginx/conf.d/default.conf.disabled-20260725`.
- `nginx -t`: PASS
- `systemctl is-active nginx`: PASS
- `systemctl is-enabled nginx`: PASS
- `GET http://127.0.0.1/healthz`: HTTP 200, body `ok`
- Port 80: listening; port 443: not listening

## Test Server

- SSH target: `my-second-brain-server` / `192.168.101.220` (test only)
- Current: `nginx/1.28.3`, Ubuntu package `1.28.3-2ubuntu1.8`
- Ubuntu repositories have no newer candidate; the package changelog records the latest CVE-2026-42533 patch as disabled because of an ABI regression.
- Official Nginx stable repository supports Ubuntu 26.04, but installing it requires root privileges.
- SSH user is in the `sudo` group, but `sudo -n` reports `interactive authentication is required`; no test-server Nginx package or config was changed.
- Existing Nginx config is `reference-project.conf`, with `server_name _` and HTTP/80 only; it is not the SaaS reverse-proxy configuration.
- `systemctl is-active nginx`: PASS
- `systemctl is-enabled nginx`: PASS
- Port 80: listening; port 443: not listening
- Test-server upgrade result: `BLOCKED` pending non-interactive sudo authorization or a user-executed root command.

## Domain / HTTPS

- No real domain was found in the repository or either Nginx configuration; only the placeholder `real-pre.YOUR_DOMAIN` exists.
- Neither host has an existing certificate or 443 listener.
- HTTPS result: `BLOCKED` pending the real domain, DNS records, and certificate issuance method. The test host has private IP `192.168.101.220`, so public DNS/Let’s Encrypt cannot point directly to it without an externally reachable ingress or internal CA/DNS.

## Build / Docker / Business Validation

- Build: not applicable; this task changed no source code.
- Docker: not changed by this task.
- Business validation: production Nginx health proxy PASS; SaaS business API mutation was not executed.
- Formal Jenkins release: not invoked.

## Conclusion

`PARTIAL`: production Nginx is upgraded to official stable 1.30.4 and healthy; test-server upgrade and domain HTTPS remain blocked by missing sudo authorization and missing domain/DNS/certificates.

## Residual Risk

- Until the test server reaches official stable 1.30.4+, it remains below the current upstream security fixed-version line.
- Until a domain and certificate are supplied, access remains HTTP/IP-based and there is no HTTPS URL to provide.
- Production package/config rollback has been prepared but not exercised.

## Retro

The upgrade exposed a package-replacement hazard: the official package removed Ubuntu `nginx-common`, briefly changed the included config tree, and prompted for a modified init script. Future upgrades must simulate package conflicts, snapshot `/etc/nginx`, run `nginx -t`, and verify the application endpoint before declaring success. See `harness/reports/current/retro-nginx-upgrade.md`.
