# Sprint 1 — Bilan (patient-service, gateway-service, frontend-service)

> **Statut : Sprint 1 terminé, testé end-to-end.**

## Ce qui existe

Trois services opérationnels, lancés ensemble (patient 8081, gateway 8080, front 8082). Stack : Java 21, Spring Boot 3.5.16, Maven mono-repo (pom parent agrégateur `medilabo-solutions`).

- **patient-service** (8081) — CRUD patient, PostgreSQL via JPA.
- **gateway-service** (8080) — Spring Cloud Gateway, point d'entrée unique, sécurité.
- **frontend-service** (8082) — UI Thymeleaf sobre, indépendante.

## patient-service

- CRUD REST complet sous `/patients` (GET liste, GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}`).
- Entité `Patient` : firstName, lastName, **birthDate** (D majuscule — nom de champ à respecter côté clients), gender (enum `Gender { M, F }`, `EnumType.STRING`), address/phone optionnels. Colonnes contraintes (`nullable=false` sur requis, `length` 100/255/20).
- DTO d'entrée `PatientRequest` avec validation durcie : `@NotBlank @Size(max=100) @Pattern([\p{L} '-]+)` sur nom/prénom (pas de chiffres, mais « O'Brien »/« Jean-Pierre » OK), `@NotNull @Past` birthDate, `@NotNull` gender, `@Size(max=255)` address, `@Size(max=20) @Pattern([0-9 +().-]*)` phone (`*` autorise le vide).
- **Déduplication métier** : un patient identique existant est rejeté → `DuplicatePatientException` → **409 Conflict** sur create/update.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) : `PatientNotFoundException`→404, `DuplicatePatientException`→409, `MethodArgumentNotValidException`→400. Corps JSON `{timestamp, status, message, errors}`.
- Seeding : `data.sql` idempotent des 4 patients OPC (spécifique SQL — ne pas transposer tel quel à Mongo au S2).
- Tests verts (service unitaire + controller `@WebMvcTest` sur H2).

## gateway-service

- Spring Cloud Gateway **WebFlux** (réactif), train 2025.0.3, starter `spring-cloud-starter-gateway-server-webflux`.
- Route `/patients/**` → patient-service (URI en dur, pas de service discovery).
- Piège config train 2025.0 : préfixe `spring.cloud.gateway.server.webflux.routes`.
- Config **YAML** (routes arborescentes) ; les autres services sont en `.properties`.

## frontend-service

- Spring MVC + Thymeleaf server-side (PAS de SPA), Bootstrap via WebJar (zéro CSS custom, pas de Node).
- **RestClient** pour appeler la gateway (pas WebClient/RestTemplate). Appelle toujours la gateway (8080), jamais patient-service en direct.
- **Login maison, PAS de Spring Security** : page login → `verifyCredentials()` teste les creds sur la gateway → creds stockés en `HttpSession` → rejoués en HTTP Basic sur chaque appel. `AuthInterceptor` (`HandlerInterceptor`) protège toutes les routes sauf login/logout/statiques.
- **Un composant parle à la gateway** : `PatientGatewayClient` (findAll, findById, create, update, delete, verifyCredentials). Controllers = orchestration MVC.
- **Lecture/écriture séparées** : `PatientDto` (record, lecture GET) vs `PatientForm` (mutable, Bean Validation, formulaires). Mapping manuel, pas de mapper framework.
- **Verbes HTTP** : formulaires en GET/POST ; le front expose `POST /patients/{id}` (update) et `POST /patients/{id}/delete`, le client traduit vers PUT/DELETE.
- `gender` manipulé en String "M"/"F" côté front (pas de copie de l'enum back → pas de couplage).
- Validation `PatientForm` **alignée sur le back** (mêmes `@Size`/`@Pattern`, `*` sur phone), messages FR. Blocs `th:errors` sur tous les champs.
- Gestion d'erreur : `Unauthorized`→redirect login ; `NotFound`→redirect liste + flash (`RedirectAttributes`, PRG) ; `Conflict` (409)→« Un patient identique existe déjà. » ; `HttpClientErrorException` générique→réaffiche form (évite Whitelabel). `error.html` auto via `BasicErrorController`.
- Logging : `log.warn` sur échecs d'appel gateway (action + statut HTTP). **Jamais de credential ni de donnée patient** (RGPD).
- Pas de `.env` (creds via login utilisateur).

## Sécurité (définitive, post-revue mentor)

Trois segments, trois mécanismes :

| Segment | Mécanisme |
|---|---|
| navigateur ↔ frontend | Session serveur (`JSESSIONID`) |
| frontend ↔ gateway | HTTP Basic (creds utilisateur rejoués depuis la session) |
| gateway ↔ back | Secret partagé (header `X-Gateway-Secret`) |

- **Auth utilisateur centralisée à la gateway** : HTTP Basic, 1 user in-memory, BCrypt. Style `SecurityWebFilterChain`/`ServerHttpSecurity` (WebFlux).
- **Provenance** : `GatewaySecretFilter` (`OncePerRequestFilter`) sur patient-service valide `X-Gateway-Secret` (constant-time, fail-fast si absent au démarrage) → 403 si absent/faux. La gateway injecte le secret via `AddRequestHeader` sur la route.
- Validé : `:8080` sans auth→401, avec Basic→200 ; `:8081` direct sans secret→403, avec→200.
- Secrets via `.env` par service (gitignore, `.env.example` versionné).

## À retenir pour les sprints suivants

- **Nouveau service back (S2 notes-service, S3 assessment)** : doit porter le `GatewaySecretFilter` (même pattern) + sa route gateway doit ajouter `X-Gateway-Secret` via `AddRequestHeader`.
- **Front au S2** : ajouter un `NoteGatewayClient` (un client par microservice). C'est à ce moment qu'on mutualisera la mécanique d'auth (lecture session + header Basic), pas avant (YAGNI).
- **notes-service** : persistance MongoDB — seeding via `CommandLineRunner` ou fichier d'init Mongo, PAS `data.sql`.
- **Contrat patient** : le champ date est `birthDate` (D majuscule) dans le JSON. Tout client doit s'aligner dessus (bug rencontré et corrigé au S1).
- **Validation** : règle projet = back rempart d'intégrité (format + longueur + dédup), front réplique les mêmes règles pour l'UX, sans partage de code.