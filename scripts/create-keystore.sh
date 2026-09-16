#!/usr/bin/env bash
set -euo pipefail
OUTPUT="${1:-money-manager-release.jks}"
ALIAS="${2:-money-manager}"
keytool -genkeypair -v -keystore "$OUTPUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000
printf 'Created %s\nBACK UP THIS FILE. Never commit it to Git.\n' "$OUTPUT"
