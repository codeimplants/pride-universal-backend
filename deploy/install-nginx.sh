#!/usr/bin/env bash
# Run ONCE on the server, by someone with sudo, after the DNS A records for both hostnames point here:
#
#   sudo bash ~/pride/install-nginx.sh
#
# Installs the two Pride Universal nginx sites (dev + prod API), reloads nginx and asks certbot for
# their certificates. Touches no other site. Safe to re-run: existing files are left as they are.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo "Run with sudo."; exit 1; }
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOSTS=(dev.api.pride.codeimplants.com api.pride.codeimplants.com)

for h in "${HOSTS[@]}"; do
  ip="$(getent ahostsv4 "$h" | awk 'NR==1{print $1}')"
  [[ -n "$ip" ]] || { echo "DNS for $h does not resolve yet; add its A record first."; exit 1; }
  echo "$h → $ip"
done

for h in "${HOSTS[@]}"; do
  if [[ ! -f "/etc/nginx/sites-available/$h" ]]; then
    install -m 644 "$HERE/nginx/$h" "/etc/nginx/sites-available/$h"
    echo "installed $h"
  fi
  ln -sf "/etc/nginx/sites-available/$h" "/etc/nginx/sites-enabled/$h"
done

nginx -t
systemctl reload nginx
for h in "${HOSTS[@]}"; do
  certbot --nginx --non-interactive --redirect -d "$h" || echo "certbot failed for $h (is its DNS pointing here?)"
done
nginx -t && systemctl reload nginx
echo "Done. Check: curl -s https://dev.api.pride.codeimplants.com/api/auth/otp/send -X POST -H 'Content-Type: application/json' -d '{}'"
