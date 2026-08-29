# Retro: Nginx Upgrade

## Actionable Improvement

Add an infrastructure runbook step before replacing a distro Nginx package:

1. Run the exact package transaction with `apt-get -s` and inspect removals/conflicts.
2. Back up `/etc/nginx` and record enabled-site symlinks.
3. Use a non-interactive conffile policy explicitly, preserving local service integration when intended.
4. After installation, run `nginx -t`, start/reload the service, and verify a real application endpoint.

## Owner / Verification

- Owner: infrastructure maintainer
- Verification: a future Nginx upgrade evidence report must show the simulation, backup path, configuration test, service state, and application HTTP result.
