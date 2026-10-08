#!/usr/bin/env bash
# One-time server setup for Hisab on a fresh Ubuntu VM (Oracle Cloud Always Free,
# Google Cloud e2-micro, or any VPS). Run as a user with sudo:
#
#   curl -fsSL https://raw.githubusercontent.com/SangamAryal/hisab/main/deploy/setup.sh | bash -s -- api.example.com
#
# The argument is the domain the app will call. No domain? Use the free
# sslip.io name for your server's public IP, e.g. 140-238-1-2.sslip.io
set -euo pipefail
DOMAIN="${1:?usage: setup.sh <domain>}"
REPO="${HISAB_REPO:-https://github.com/SangamAryal/hisab.git}"

sudo apt-get update -y
sudo apt-get install -y git unzip curl debian-keyring debian-archive-keyring apt-transport-https

# Caddy: HTTPS reverse proxy with automatic Let's Encrypt certificates.
if ! command -v caddy >/dev/null; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
  sudo apt-get update -y && sudo apt-get install -y caddy
fi

# App code and PocketBase binary in /opt/hisab
sudo mkdir -p /opt/hisab && sudo chown "$USER" /opt/hisab
if [ -d /opt/hisab/repo/.git ]; then git -C /opt/hisab/repo pull --ff-only; else git clone "$REPO" /opt/hisab/repo; fi
/opt/hisab/repo/backend/get-pocketbase.sh

# Service user and systemd unit
id hisab >/dev/null 2>&1 || sudo useradd --system --home /opt/hisab --shell /usr/sbin/nologin hisab
sudo mkdir -p /opt/hisab/data && sudo chown -R hisab:hisab /opt/hisab/data
sudo cp /opt/hisab/repo/deploy/hisab.service /etc/systemd/system/hisab.service
sudo systemctl daemon-reload
sudo systemctl enable --now hisab

# Caddy site
sed "s/{{DOMAIN}}/$DOMAIN/" /opt/hisab/repo/deploy/Caddyfile | sudo tee /etc/caddy/Caddyfile >/dev/null
sudo systemctl reload caddy

# Open the firewall inside the VM (Oracle images block 80/443 by default).
if command -v iptables >/dev/null; then
  sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT || true
  sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT || true
  sudo sh -c 'command -v netfilter-persistent >/dev/null && netfilter-persistent save' || true
fi

echo
echo "Done. Create the admin account with:"
echo "  sudo -u hisab /opt/hisab/repo/backend/pocketbase superuser upsert you@example.com 'a-long-password' --dir=/opt/hisab/data"
echo "Then open https://$DOMAIN/_/ and build the app with -Phisab.apiUrl=https://$DOMAIN"
