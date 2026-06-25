# CLAUDE.md — MédiLabo Solutions

> Cadrage Claude Code. Mono-repo, projet de fin de formation (Concepteur d'Applications Java, Bac+3). Microservices Spring d'évaluation du risque de diabète de type 2 pour la clinique Abernathy.

---

## Contexte projet

Application aidant les médecins à identifier les patients à risque (soins préventifs). 5 microservices, déployés ensemble via un `docker-compose.yml` unique à la racine. Développeur solo, mono-repo car déploiement couplé.

| Service | Rôle | Persistance |
|---|---|---|
| `patient-service` | Données démographiques patient (CRUD) | PostgreSQL |
| `notes-service` | Notes médecin (non structurées) | MongoDB |
| `assessment-service` | Calcul du risque diabète | Aucune (interroge patient + notes) |
| `gateway-service` | Point d'entrée unique + sécurité | — |
| `frontend-service` | Interface sobre, indépendante du back | — |

---

## Stack (imposée)

Java 21 · **Spring Boot 3.5.x** · Maven · PostgreSQL via Spring Data JPA · MongoDB via Spring Data MongoDB · Spring Cloud Gateway · Spring Security · Lombok · Docker Compose.

> ⚠️ **Ne PAS utiliser Spring Boot 4.x.** La 4.x (baseline Spring Framework 7) introduit la modularisation des starters de test (le package de `@WebMvcTest` change) et le passage à Jackson 3 (package `com.fasterxml.jackson` → `tools.jackson`, `ObjectMapper` → `JsonMapper`). Ces changements cassent le code calé sur les conventions 3.x et désalignent des cours/doc OPC. **Tout le projet reste en Spring Boot 3.5.x** (dernière 3.5 : 3.5.15). Générer chaque nouveau module en 3.5.x sur start.spring.io. Vérifier que la version de Spring Cloud sélectionnée est bien celle compatible 3.5.x.

### Outillage

- **Build/run en ligne de commande : `mvn` (Maven système installé), PAS `./mvnw`.** Le wrapper `./mvnw` est muet sous Git Bash dans cet environnement ; le `mvn` système (sur JDK 21) fonctionne. Utiliser `mvn clean test`, `mvn spring-boot:run`, etc.
- Shell : Git Bash. Lancement de chaque service back via un script `run-dev.sh` qui charge un `.env` (variables DB) avant `mvn spring-boot:run`.

---

## Rôle de Claude Code

Implémenteur, pas décideur. Les choix d'architecture, de design et de trade-offs se font côté Claude Web. Face à une ambiguïté architecturale → poser la question, ne pas trancher seul. Avant de coder une tâche non triviale : exposer en quelques lignes l'approche retenue, puis le code, puis la validation à demander.

Méthode de travail : **paliers courts**. Claude Code implémente un palier → review côté Web → commit par le développeur → palier suivant. Préférer plusieurs petits paliers vérifiables à une grosse génération d'un bloc.

---

## Architecture & conventions

### Structure en couches (back), par service

```
controller/   → endpoints REST, reçoit/renvoie des DTO
service/      → logique métier
repository/   → accès données (Spring Data)
model/        → entités (JPA pour patient, documents pour notes)
dto/          → objets exposés par l'API
mapper/       → mapping entité ↔ DTO (composant @Component injecté, pas statique)
exception/    → exceptions métier nommées + @RestControllerAdvice
```

### Conventions

- Code en anglais (classes, méthodes, variables, packages) ; commentaires et doc en français.
- Packages : `com.medilabo.<service>` (ex. `com.medilabo.patientservice`).
- **DTO obligatoires** : ne jamais exposer les entités directement. Mapping via la couche `mapper`. Le service parle DTO, jamais l'entité vers l'extérieur.
- Java : PascalCase (classes), camelCase (méthodes/variables), UPPER_SNAKE_CASE (constantes). DB : snake_case (Hibernate convertit camelCase → snake_case). REST : chemins en kebab-case pour les ressources multi-mots, **pas de versionning `/api/v1/`** (non demandé par l'énoncé ; les chemins restent simples, ex. `/patients`).
- Injection par constructeur uniquement (pas de `@Autowired` sur champ).
- Javadoc sur l'API publique. Commentaires inline sur le *pourquoi* non-évident, pas sur les getters/setters/constructeurs triviaux.
- Lombok pour le boilerplate (getters/setters, constructeurs, `@Slf4j`), sans masquer de logique.
- Enums persistés en `EnumType.STRING` (lisibilité base + robustesse au réordonnancement), jamais `ORDINAL`.

### Pragmatisme (important en contexte d'évaluation)

SOLID = boussole, pas dogme. Chaque abstraction doit se justifier par un besoin réel du périmètre : pas d'interface de service « par principe », pas de généricité spéculative, pas de couche supplémentaire (façade, use-cases) au-delà de `controller/service/repository`.

---

## Périmètre & garde-fous

- **Périmètre figé, critère d'évaluation explicite.** Ne jamais ajouter de fonctionnalité non demandée. En particulier NE PAS implémenter : inscription/registration, gestion de rôles/droits, front élaboré, endpoints ou entités non prévus.
- Maintenabilité ≠ sur-ingénierie : couches propres, contrats d'API clairs, faible couplage, sans anticiper de besoins hors périmètre.
- Toute demande ou implémentation qui semble sortir du périmètre → le signaler plutôt que l'exécuter silencieusement.

---

## Tests

- Approche pragmatique : **code puis tests** sur le CRUD classique (logique triviale). **TDD réservé à l'algorithme d'assessment (S3)**, où les règles précises et les cas-frontière justifient d'écrire les tests d'abord.
- Nommage explicite : `should_<expected>_when_<condition>`.
- Distinguer **unit** (logique métier : services, mappers, surtout l'algorithme d'assessment ; repository mocké) et **integration/web** (endpoints via `@WebMvcTest`, service mocké).
- Tests sur **H2 en mémoire** (scope test), prod/dev sur PostgreSQL. Config de test séparée (`src/test/resources/application.properties`) ; désactiver l'init SQL en test (`spring.sql.init.mode=never`) pour éviter les conflits de dialecte (ex. `ON CONFLICT` Postgres ≠ H2).
- Un seul `spring-boot-starter-test` couvre JUnit 5, Mockito, AssertJ, MockMvc. Ne pas inventer de starters de test par dépendance.
- Prioriser ce qui protège réellement contre la régression, pas la couverture exhaustive.

---

## Sécurité

- **Spring Security centralisé à la gateway** (point d'authentification unique ; les microservices back sont en confiance derrière). Décision validée avec le mentor, réversible si contre-ordre — l'énoncé suggérait à l'origine une security par service.
- Authentification HTTP basic, utilisateurs in-memory, mots de passe encodés **BCrypt** (obligatoire). Pas d'inscription, pas de rôles/droits.
- Config Spring Security : utiliser la **doc Spring Boot 3.5 / Spring Framework 6** (style `SecurityFilterChain` en bean, pas l'ancien `WebSecurityConfigurerAdapter` déprécié).
- **Secrets** : aucun secret, mot de passe ou credential en dur dans le code ou les fichiers versionnés. Credentials DB (Postgres, Mongo) et config sensible via variables d'environnement, `.env` dans `.gitignore` (un `.env.example` versionné sert de modèle). La config Docker Compose lit ces variables, ne les code pas en dur.
- **Validation des entrées multi-couches** : Bean Validation sur les DTOs (web, `@Valid`), règles métier en Java pur (assessment), préconditions (existence/état) dans la couche service. Chaque couche se protège, pas de confiance aveugle dans la précédente.

---

## Gestion d'erreurs

- Exceptions métier explicites et nommées (ex. `PatientNotFoundException`), définies dans un package `exception/` du service. Une exception métier **étend `RuntimeException`** (pratique correcte) ; ce qui est proscrit, c'est de lever un `throw new RuntimeException("...")` brut/générique.
- Levées dans la couche service quand une règle ou une précondition n'est pas respectée (ex. patient inexistant).
- Traduites en réponses HTTP via un `@RestControllerAdvice` (handler centralisé), qui mappe chaque exception métier vers le bon status. Séparation nette : le service *détecte*, l'advice *présente* en HTTP.
- Réponse d'erreur API cohérente : timestamp, status, message (et `errors` champ→message pour les échecs de validation `@Valid` → 400).

---

## Logging

- SLF4J via Lombok `@Slf4j`, pas de `System.out.println`. Syntaxe paramétrée `{}` (jamais de concaténation).
- Logs sur les événements métier significatifs (INFO sur les écritures : create/update/delete ; WARN sur les erreurs traitées dans l'advice). Lectures non loggées par défaut.
- **Aucune donnée patient ni secret dans les logs** (données de santé — RGPD). Logger uniquement des identifiants techniques (id) et l'action ; pour la validation, les noms de champs en erreur, jamais les valeurs saisies.

---

## Algorithme d'assessment (cœur métier)

Pièce la plus importante et la plus à risque de régression. Règles complètes dans `docs/features/assessment.md`.

- Incorporer toutes les règles (4 niveaux : None, Borderline, In Danger, Early onset).
- Recherche des termes déclencheurs **insensible à la casse** dans les notes.
- Respecter le **format de renvoi attendu** par l'endpoint.
- Zones d'ambiguïté → hypothèses explicites documentées dans le fichier feature, ne pas trancher silencieusement.
- Testé unitairement de façon poussée (TDD ici).

---

## Git & dépendances

- Commits et push gérés par le développeur. À chaque palier cohérent, **proposer un message de commit sans committer**. Convention : `feat`, `fix`, `chore`, `refactor`, `test`, `docs`. Format court (`feat(patient-service): add patient CRUD endpoints`). Granularité : un commit = une unité logique cohérente (historique propre = critère d'évaluation).
- **Aucun ajout de dépendance sans validation explicite.** Si une lib semble nécessaire, l'évoquer et attendre — ne pas l'ajouter. Vérifier les noms exacts des artifactId (ne pas inventer de starters).

---

## Documentation feature

Toute feature documentée dans `docs/features/<feature>.md` : résumé, endpoints, architecture impactée, décisions/hypothèses, comment tester, limitations.

---

## Découpage par sprints

Patient (S1) → Notes (S2) → Assessment (S3), puis Docker et Green Code. Chaque service back est généré **au moment de son sprint**. Ne pas scaffolder de service non encore abordé.

### État d'avancement

- **S1 — patient-service : terminé.** CRUD complet (5 endpoints REST sous `/patients`), architecture en couches, enum `Gender` (EnumType.STRING), validation `@Valid`, `GlobalExceptionHandler` (404/400), logging SLF4J, 21 tests verts (service unitaire + controller `@WebMvcTest` sur H2), seeding `data.sql` idempotent des 4 patients OPC (+ `setval` sur la séquence). Validé en Postman. Spring Boot 3.5.15.
- **S1 — en cours : gateway-service** (Spring Cloud Gateway + Spring Security centralisée) et **frontend-service** (UI sobre indépendante).