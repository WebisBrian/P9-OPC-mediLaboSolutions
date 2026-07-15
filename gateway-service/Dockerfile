# Stage 1 — build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Poms de tout le reactor d'abord : Maven doit résoudre l'arborescence multi-module
# complète (pom parent + 5 modules déclarés) avant de pouvoir cibler -pl gateway-service,
# même si seul ce module est construit. Permet aussi à Docker de cacher la couche de
# dépendances tant qu'aucun pom ne change.
COPY pom.xml .
COPY assessment-service/pom.xml assessment-service/
COPY patient-service/pom.xml patient-service/
COPY gateway-service/pom.xml gateway-service/
COPY frontend-service/pom.xml frontend-service/
COPY notes-service/pom.xml notes-service/
RUN mvn -pl gateway-service -am dependency:go-offline -B

# Puis les sources du seul module construit
COPY gateway-service/src gateway-service/src
RUN mvn -pl gateway-service -am clean package -DskipTests -B

# Stage 2 — runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl requis par le healthcheck docker-compose (étape suivante) ; absent de l'image JRE de base.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/gateway-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]