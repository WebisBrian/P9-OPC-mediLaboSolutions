#!/usr/bin/env bash
# Charge les variables depuis .env et lance patient-service
set -a
source .env
set +a

cd patient-service
mvn spring-boot:run
