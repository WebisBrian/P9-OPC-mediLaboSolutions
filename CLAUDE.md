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

> ⚠️ **Ne PAS utiliser Spring Boot 4.x.** La 4.x (baseline Spring Framework 7) introduit la modularisation des starters de test (le package de `@WebMvcTest` change) et le passage à Jackson 3 (package `com.fasterxml.jackson` → `tools.jackson`, `ObjectMapper` → `JsonMapper`). Ces changements cassent le code calé sur les conventions 3.x et désalignent des cours/doc OPC. **Tout le projet reste en Spring Boot 3.5.x** (pom parent : 3.5.16). Générer chaque nouveau module en 3.5.x sur start.spring.io. Vérifier que la version de Spring Cloud sélectionnée est bien celle compatible 3.5.x.

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
security/     → configuration Spring (SecurityConfig, GatewaySecretFilter de provenance)
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

### Conventions front (frontend-service)

Microservice indépendant, jamais incorporé au back. Spring MVC + Thymeleaf (server-side), pas de SPA.
- **Pas de Spring Security sur le front.** Login maison léger : page de login, credentials utilisateur stockés en `HttpSession`, rejoués en HTTP Basic vers la gateway. Un `HandlerInterceptor` protège les routes (redirect `/login` si pas de session).
- **RestClient** (pas WebClient, réactif inutile en MVC servlet ; pas RestTemplate).
- **Un client par microservice back** : `PatientGatewayClient` (et `NoteGatewayClient` au S2, etc.). Un seul composant par domaine dialogue avec la gateway ; les controllers orchestrent. Mutualiser la mécanique d'auth (lecture session + header Basic) **quand le 2e client apparaît** (S2), pas avant (YAGNI).
- **Séparation lecture/écriture** : DTO record immuable pour la lecture (désérialisation GET), classe Form mutable + Bean Validation pour les formulaires. Mapping manuel (pas de mapper framework côté front).
- **Traduction verbes HTTP** : les formulaires HTML ne font que GET/POST ; le front expose `POST /x/{id}` (update) et `POST /x/{id}/delete`, le client traduit vers PUT/DELETE côté gateway.
- **Pas de duplication d'un type interne du back** (ex. enum `Gender`) : manipuler des String côté front pour éviter le couplage.
- **Bootstrap via WebJar**, zéro CSS custom, pas de toolchain Node. UI sobre (objectif démo).
- Le `mapper/` obligatoire de la structure back **ne s'applique pas** au front (mapping manuel léger).

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

Les services back portant le GatewaySecretFilter doivent fournir gateway.secret aux tests qui chargent le contexte (@TestPropertySource ou config de test), sinon le fail-fast empêche le démarrage. Isoler les tests @WebMvcTest de la sécurité via excludeAutoConfiguration.

---

## Sécurité

Trois segments, trois mécanismes (décision définitive, validée mentor) :

| Segment | Mécanisme |
|---|---|
| navigateur ↔ frontend | Session serveur (`JSESSIONID`) |
| frontend ↔ gateway | HTTP Basic (creds utilisateur rejoués depuis la session) |
| gateway ↔ services back | Secret partagé (header `X-Gateway-Secret`) |

- **Auth utilisateur centralisée à la gateway** : HTTP Basic, utilisateurs in-memory, mots de passe BCrypt (obligatoire). Pas d'inscription, pas de rôles. Gateway = WebFlux réactif → `SecurityWebFilterChain` + `ServerHttpSecurity` en bean (PAS `SecurityFilterChain`/`HttpSecurity`, qui sont l'API servlet réservée aux backs ; PAS de `WebSecurityConfigurerAdapter` déprécié).
- **Stack de sécurité selon le module** : gateway = réactive (`SecurityWebFilterChain` + `ServerHttpSecurity`) car WebFlux ; services back = servlet (`SecurityFilterChain` + `HttpSecurity`) car MVC/Tomcat. Ne jamais copier le pattern d'un module vers l'autre sans adapter la stack.
- **Provenance des requêtes (chaque service back)** : un `GatewaySecretFilter` (`OncePerRequestFilter`) valide le header `X-Gateway-Secret` (comparaison constant-time, fail-fast si le secret est absent au démarrage). Absent/faux → 403. La gateway injecte le secret via `AddRequestHeader` sur la route du service. **Tout nouveau service back (ex. notes-service au S2) doit porter ce filtre + sa route gateway doit ajouter le header.**
- Principe : *centralisé ≠ exclusif*. Le back ne ré-authentifie pas l'utilisateur ; il vérifie seulement qu'une requête vient bien de la gateway. Défense en profondeur = secret + isolation réseau Docker (S5).
- **Secrets** : aucun secret en dur. Credentials (auth gateway, DB Postgres/Mongo, `X-Gateway-Secret`) via variables d'env / `.env` (gitignore, `.env.example` versionné). Docker Compose lit ces variables.
- Le **front n'a pas de `.env`** : pas de compte de service, les credentials viennent de l'utilisateur via une page de login (login maison léger, pas de Spring Security sur le front — voir conventions front).

### Validation (multi-couches, règle du projet)
- **Back = rempart d'intégrité** : Bean Validation `@Valid` sur les DTO d'entrée (présence + format + longueur), règles métier en Java (assessment), préconditions dans le service. Le back ne fait jamais confiance à l'extérieur, **y compris au frontend** (un appel direct à la gateway contourne le front).
- **Front = UX** : mêmes règles de format/longueur répliquées sur les formulaires (feedback immédiat), mais le front ne garantit rien — il ne fait que du confort.
- **Règles identiques des deux côtés, SANS partage de code** (pas de module commun : couplage entre microservices écarté). Discipline de cohérence, pas de dépendance partagée.
- Règles patient de référence : nom/prénom `@Size(max=100)` + `@Pattern([\p{L} '-]+)` (lettres Unicode + espace/tiret/apostrophe, pas de chiffres) ; phone `@Size(max=20)` + `@Pattern([0-9 +().-]*)` ; address `@Size(max=255)`.
- **Déduplication métier** (relationnelle, back uniquement — le front ne peut pas la vérifier localement) : un doublon lève une exception métier → **409 Conflict**, que le front traduit en message utilisateur clair.
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
- **S1 — TERMINÉ** (patient-service, gateway-service, frontend-service).
    - patient-service : CRUD `/patients`, validation durcie (format/longueur), dédup patient (409), `GlobalExceptionHandler` (404/409/400), `GatewaySecretFilter` (provenance), seeding 4 patients OPC, tests verts. SB 3.5.x.
    - gateway-service : Spring Cloud Gateway, route `/patients/**`, auth Basic centralisée (BCrypt, in-memory), injection `X-Gateway-Secret` vers le back.
    - frontend-service : UI Thymeleaf sobre, login maison, `PatientGatewayClient`, CRUD complet, validation alignée + gestion 409.
    - Détail complet : `docs/SPRINT1.md` (si généré) ou rapport de fin de sprint.
- **S2 — à démarrer** : notes-service (MongoDB) + `NoteGatewayClient` au front + route gateway avec secret. notes-service doit reproduire le `GatewaySecretFilter`.