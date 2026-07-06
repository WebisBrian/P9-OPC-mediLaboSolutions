#!/usr/bin/env bash
set -e

VALID_SERVICES="patient-service gateway-service frontend-service notes-service"
USAGE="Usage: $0 <service>
Services disponibles : $VALID_SERVICES"

if [ $# -eq 0 ]; then
    echo "$USAGE"
    exit 1
fi

SERVICE="$1"

FOUND=0
for s in $VALID_SERVICES; do
    [ "$SERVICE" = "$s" ] && FOUND=1 && break
done

if [ $FOUND -eq 0 ]; then
    echo "Erreur : service inconnu '$SERVICE'"
    echo "$USAGE"
    exit 1
fi

ENV_FILE="$SERVICE/.env"
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

cd "$SERVICE"
mvn spring-boot:run