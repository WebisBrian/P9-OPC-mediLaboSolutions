#!/usr/bin/env bash
# Génère la paire de clés RS256 (JWT gateway -> services back) et les place
# aux emplacements attendus par chaque module Spring.
#
# Usage : ./scripts/generate-keys.sh [--force]
set -e

FORCE=0
if [ "$1" = "--force" ]; then
    FORCE=1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

PRIVATE_KEY_PATH="$ROOT_DIR/gateway-service/src/main/resources/keys/private_key.pem"

PUBLIC_KEY_PATHS=(
    "$ROOT_DIR/patient-service/src/main/resources/keys/public_key.pem"
    "$ROOT_DIR/notes-service/src/main/resources/keys/public_key.pem"
    "$ROOT_DIR/assessment-service/src/main/resources/keys/public_key.pem"
)

EXISTING=0
if [ -f "$PRIVATE_KEY_PATH" ]; then
    EXISTING=1
fi
for pub in "${PUBLIC_KEY_PATHS[@]}"; do
    [ -f "$pub" ] && EXISTING=1
done

if [ "$EXISTING" -eq 1 ] && [ "$FORCE" -ne 1 ]; then
    read -r -p "Des clés existent déjà. Les écraser ? [y/N] " REPLY
    case "$REPLY" in
        y|Y|yes|YES) ;;
        *) echo "Annulé."; exit 0 ;;
    esac
fi

mkdir -p "$(dirname "$PRIVATE_KEY_PATH")"
for pub in "${PUBLIC_KEY_PATHS[@]}"; do
    mkdir -p "$(dirname "$pub")"
done

# Clé privée RSA 2048 bits, format PKCS#8 PEM (BEGIN PRIVATE KEY)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PRIVATE_KEY_PATH"

for pub in "${PUBLIC_KEY_PATHS[@]}"; do
    # Clé publique dérivée, format X.509/SPKI PEM (BEGIN PUBLIC KEY)
    openssl pkey -in "$PRIVATE_KEY_PATH" -pubout -out "$pub"
done

echo "Clés RS256 générées avec succès :"
echo "  - $PRIVATE_KEY_PATH"
for pub in "${PUBLIC_KEY_PATHS[@]}"; do
    echo "  - $pub"
done