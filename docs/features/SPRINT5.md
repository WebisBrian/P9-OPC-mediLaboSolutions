# Sprint 5 — Bilan (dockerisation : Dockerfiles multi-stage + docker-compose unique)

> **Statut : Sprint 5 terminé, testé end-to-end.**
> **Nature : étape d'infrastructure (guide OPC étape 5).** Ne répond à aucune user story métier : rend l'application déployable et lançable par un évaluateur avec Docker Desktop seul, sans installer Java, Maven, PostgreSQL ni MongoDB. Aucune fonctionnalité métier ajoutée.

## Objectif du sprint

Passer d'un projet lançable service par service (Maven/IDE local) à un ensemble conteneurisé démarrable en deux commandes. Cible explicite : **simplifier la tâche de l'évaluateur** — un développeur disposant de Docker Desktop clone, exécute deux commandes, et obtient l'application fonctionnelle.

## Ce qui existe

Aucun nouveau microservice. Les 5 services (gateway 8080, patient 8081, front 8082, notes 8083, assessment 8084) sont **inchangés fonctionnellement**. Le sprint ajoute la couche de conteneurisation et externalise la configuration réseau. Stack alignée : Java 21, Spring Boot 3.5.16, Maven mono-repo.

| Composant | Ajouté / modifié |
|---|---|
| **5 microservices** | 1 `Dockerfile` multi-stage chacun + dépendance `spring-boot-starter-actuator` + `/actuator/health` ouvert |
| **gateway** | 3 URIs de routes externalisées (`${PATIENT_SERVICE_URL:…}`, etc.) |
| **frontend** | `gateway.base-url` externalisée (`${GATEWAY_BASE_URL:…}`) |
| **patient / notes** | datasource / URI Mongo externalisées (déjà partiellement fait au S2) |
| **racine** | `docker-compose.yml` unique (7 services), `.dockerignore`, `.env.example` |
| **suppressions** | scripts de lancement local `run-dev.sh` / `run-dev.ps1` retirés (Docker devient le mode unique) |

Bilan net : le lancement du projet passe de « lancer 5 services + 2 bases à la main » à `docker compose up --build`.

## Dockerfiles (5, un par service)

- **Multi-stage** : stage 1 `maven:3.9-eclipse-temurin-21` (build), stage 2 `eclipse-temurin:21-jre` (runtime). L'évaluateur n'a besoin ni de JDK ni de Maven — le build Maven se fait dans le conteneur.
- **Contexte de build = racine du repo** (pas le dossier du module) : le mono-repo Maven impose que le pom parent et les 5 poms enfants soient présents pour que le reactor démarre. Chaque Dockerfile copie donc **les 5 poms** avant les sources, puis builde le seul module ciblé via `mvn -pl <module> -am`.
- **Ordre des `COPY` optimisé pour le cache Docker** : poms d'abord (`dependency:go-offline`), sources ensuite. Tant qu'aucun pom ne change, la couche de dépendances (longue) reste en cache même quand le code change.
- **`-DskipTests` au build image** : les tests tournent en local/CI, pas à chaque `docker build` (sinon les tests Testcontainers exigeraient un Docker dans Docker).
- **`curl` installé dans le stage runtime** (absent de l'image JRE de base) : nécessaire aux healthchecks du compose.
- **Clés RS256** : embarquées dans le jar via `classpath:keys/` au build → suivent naturellement dans l'image, aucun volume à monter.

## docker-compose.yml (unique, racine)

**7 services** : 5 applicatifs + `postgres:16` + `mongo:7`.

- **Noms de services = hostnames DNS internes**, cohérents avec les modules Maven (`patient-service`, `notes-service`, etc.). La communication inter-services passe par ces noms (`http://patient-service:8081`), jamais par `localhost`. `container_name: medilabo-*` ajouté pour un affichage lisible dans Docker Desktop (sans effet sur la résolution DNS).
- **Un seul port publié : `8082` (frontend).** Ni les backs, ni la gateway, ni les bases ne sont exposés sur l'hôte — ils vivent sur le réseau bridge `medilabo-net`. La gateway comme point d'entrée unique est ainsi matérialisée par le réseau, pas seulement par convention.
- **Variables d'environnement** : chaque service reçoit les URLs de ses dépendances pointant vers les noms de services Docker. Défauts fonctionnels via `${VAR:-defaut}` → `docker compose up` marche **sans créer de `.env`**.
- **Volumes nommés** (`postgres-data`, `mongo-data`) : persistance des bases entre redémarrages.

## Healthchecks & ordre de démarrage

Point structurant du sprint. `depends_on` seul ne garantit que l'ordre de *démarrage des conteneurs*, pas la *disponibilité* du service. Un back qui démarre avant que sa base accepte les connexions crashe.

- **Bases** : healthchecks natifs — `pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB` (Postgres), `mongosh --eval "db.adminCommand('ping')"` (Mongo). Le `$$` force l'évaluation de la variable *dans* le conteneur, sans dupliquer le défaut.
- **Applications** : `curl -f http://localhost:<port>/actuator/health` (d'où Actuator, ajouté sur les 5 services, exposant `health` uniquement). `start_period: 40s` pour laisser Spring Boot démarrer sans boucle de redémarrage.
- **`depends_on: condition: service_healthy`** en cascade : `postgres/mongo → patient/notes → assessment → gateway → frontend`. Chaque niveau n'attend le suivant qu'une fois réellement prêt. Démarrage ordonné, sans crash au premier `up`.

## Actuator & sécurité

- Dépendance `spring-boot-starter-actuator` sur les 5 services, **exposition réduite à `health`** (`management.endpoints.web.exposure.include=health`) : pas de fuite via `env`, `beans`, `mappings`.
- `/actuator/health` **ouvert sans authentification** dans les 4 chaînes de sécurité (les 3 resource-servers backs + la gateway WebFlux) et dans l'`AuthInterceptor` du front. C'est la seule modification du modèle de sécurité S4, assumée : un healthcheck ne s'authentifie pas.
- **Pas de route `/actuator/**` dans la gateway** : les healthchecks Docker appellent chaque conteneur directement sur le réseau interne. Router Actuator par la gateway exposerait publiquement la santé des backs via le seul port publié — évité.

## Décisions d'architecture (justifications soutenance)

1. **Bases conteneurisées, une par service.** Non exigé par OPC, mais retenu pour la simplicité évaluateur (aucune installation de SGBD). Chaque base dans **son propre conteneur** (jamais dans le conteneur de l'app) : principe « un processus par conteneur » et *database-per-service*. Coupler app et base casserait l'indépendance de déploiement — le fondement du découpage microservices. L'app parle à la base via le réseau (`postgres:5432`, `mongo:27017`), elle ne l'héberge pas.

2. **Multi-stage plutôt que build Maven hors Docker.** Garantit que l'évaluateur n'a que Docker à installer. Coût : premier build plus long (~5–10 min, dépendances Maven + images de base). Arbitrage assumé : la simplicité de mise en route prime sur le temps du premier build (les suivants sont cachés).

3. **Un seul port publié (8082).** Choix de sécurité, pas seulement de configuration : réduit la surface d'attaque au strict nécessaire et fait du réseau Docker le garant de « la gateway est l'unique point d'entrée ». Cohérent avec le modèle de sécurité durci au S4.

4. **Défauts fonctionnels dans le compose (`${VAR:-defaut}`).** L'évaluateur lance sans créer de `.env`. Les vrais secrets restent externalisables/surchargeables via `.env` (gitignoré), `.env.example` documente chaque variable. Compromis entre « zéro friction en démo » et « secrets hors du repo ».

5. **Healthchecks comme contrat de démarrage.** Actuator n'est pas là pour du monitoring (hors périmètre) mais pour donner à Docker une sonde de disponibilité, seul moyen d'orchestrer un démarrage ordonné via `service_healthy`. Observabilité minimale, justifiée par un besoin concret d'infrastructure.

6. **Suppression de `run-dev.sh` / `run-dev.ps1`.** Docker devient le mode de lancement unique et documenté. Les scripts de lancement local, non maintenus depuis le S4 (JWT), devenaient un risque d'incohérence (promesse non tenue dans le README). Les défauts `localhost` des configs restent en place : le débogage service-par-service depuis l'IDE demeure possible, seul l'outillage d'automatisation est retiré.

## Hypothèses posées

- **Clés RS256 générées par script, jamais committées.** `./scripts/generate-keys.sh` est un prérequis au lancement (fail-fast si clé absente, hérité du S4). Deux commandes plutôt qu'une seule, mais cohérent avec la posture « une clé privée ne se commit pas, même en démo » — assumée en soutenance contre l'alternative (committer une paire de démo) qui aurait contredit le S4.
- **`start_period: 40s` sur les healthchecks applicatifs.** Estimation prudente du temps de démarrage Spring Boot en conteneur, pour éviter les faux négatifs (conteneur marqué unhealthy avant d'avoir fini de démarrer). Ajustable si les temps réels diffèrent.
- **Variable `PATIENT_SERVICE_URL` / `NOTES_SERVICE_URL` partagées gateway ↔ assessment.** Les deux pointent vers la même cible, donc une variable commune. Léger couplage de configuration assumé à cette échelle (choix de simplicité ; dédoublement possible si les chemins divergeaient un jour).

## Écarts & points en suspens

- **Bruit de logs Mongo (`Connection not authenticating`).** Le healthcheck `mongosh ping` toutes les 5 s génère ces lignes à chaque cycle — normal (Mongo sans auth en démo), inoffensif, non supprimé. Peut être espacé (`interval` plus long) si gênant.
- **Pas d'authentification sur MongoDB en démo.** Cohérent avec le périmètre (démo locale). En production, Mongo serait protégé par credentials + réseau.
- **Base-urls d'assessment / clés en `classpath:`.** Fonctionnent en conteneur (embarquées au build). Un montage par volume resterait possible (chemins surchargeables) si un déploiement le préférait — non nécessaire ici.
- **Limites héritées inchangées** : notes orphelines (S2), couplage de disponibilité d'assessment (S3), Basic non chiffré front→gateway sans TLS (S4). Aucune n'est affectée par ce sprint.

## À reprendre au Sprint 6 (Green Code)

- **Livrable = section README** (analyse + pistes de refactoring localisées), au niveau d'exigence de la **grille** d'autoévaluation (pas seulement « savoir expliquer » : savoir *appliquer* et proposer des *pistes concrètes*), conformément à la divergence guide/grille tranchée dès le fichier de contexte.
- **Points d'analyse concrets déjà identifiés** au fil des sprints :
    - coût crypto par requête (signature RSA non mise en cache, S4) — chiffrable, avec pistes volontairement non implémentées (cache de token, EdDSA) : savoir *ne pas* optimiser à cette échelle fait partie de l'analyse ;
    - service assessment stateless recalculant à chaque appel + comptage lexical des déclencheurs (S3) ;
    - images Docker : taille, layers, réutilisation du cache — angle Green Code naturel de ce sprint.

## Validation end-to-end

- `docker compose up --build` sur volume vierge (`down -v` préalable) → 7 conteneurs, tous `healthy`, aucun en boucle de redémarrage.
- Chaîne `depends_on: service_healthy` respectée dans l'ordre attendu (bases → patient/notes → assessment → gateway → frontend).
- Front accessible sur `http://localhost:8082` : login (`admin` / `medilabo`) → liste des 4 patients → page détail avec infos patient, historique paginé des notes, badge de risque + déclencheurs. Chaîne complète front → gateway → assessment → patient + notes fonctionnelle en conteneur.
- Oracle des 4 patients (None / Borderline / In Danger / Early onset) vérifié via le navigateur.
- Idempotence du seeding confirmée après redémarrage (volumes conservés) : 4 patients, notes non dupliquées (`Note seeding skipped`).
- Base `medilabo_patient` correctement nommée, table `patients` présente ; logs Postgres sans erreur après correction du healthcheck (`-d $$POSTGRES_DB`).