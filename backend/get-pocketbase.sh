#!/usr/bin/env bash
# Downloads the official PocketBase binary for this machine into backend/.
# Usage: ./get-pocketbase.sh            (Linux/macOS, amd64/arm64)
set -euo pipefail
VERSION="${PB_VERSION:-0.40.4}"
cd "$(dirname "$0")"
os="$(uname -s | tr '[:upper:]' '[:lower:]')"
arch="$(uname -m)"
case "$arch" in
  x86_64|amd64) arch=amd64 ;;
  aarch64|arm64) arch=arm64 ;;
  *) echo "Unsupported CPU: $arch" >&2; exit 1 ;;
esac
zip="pocketbase_${VERSION}_${os}_${arch}.zip"
curl -fL -o "$zip" "https://github.com/pocketbase/pocketbase/releases/download/v${VERSION}/${zip}"
unzip -o "$zip" pocketbase >/dev/null
rm "$zip"
chmod +x pocketbase
./pocketbase --version
