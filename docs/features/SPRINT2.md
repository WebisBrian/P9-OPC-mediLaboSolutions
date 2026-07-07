# Sprint 2 — Bilan (notes-service, + évolutions gateway/frontend/patient)

> **Statut : Sprint 2 terminé, testé end-to-end.**

## Ce qui existe

Un nouveau microservice back **notes-service** (port 8083), branché à la gateway comme patient-service, plus l'affichage et l'ajout de notes côté front sur la page détail patient. Stack alignée sur le S1 : Java 21, Spring Boot 3.5.16, Maven mono-repo (`medilabo-solutions`).

- **notes-service** (8083) — CRUD partiel des notes médicales, MongoDB via Spring Data MongoDB.
- **gateway-service** (8080) — route `/notes/**` ajoutée, injection du secret de provenance.
- **frontend-service** (8082) — page détail patient enrichie : historique paginé des notes + formulaire d'ajout.
- **patient-service** (8081) — inchangé fonctionnellement ; tests migrés vers Testcontainers.
  Ports : gateway 8080, patient 8081, front 8082, **notes 8083**.

## notes-service

- **Persistance MongoDB** (base `medilabo_notes`, collection `notes`), conteneur `mongo:7` en dev local.
- Entité `Note` (`@Document`) : `String id` (`@Id`, ObjectId Mongo exposé en String), `Long patientId` (clé de liaison vers le patient SQL), `String note` (texte libre), `Instant createdAt` (`@CreatedDate`, auditing Mongo actif via `MongoAuditingConfig` `@EnableMongoAuditing`).
- **Référence pure** : la note ne stocke que `patientId`, aucune donnée patient dupliquée (pas de nom). Choix assumé (voir décisions).
- `NoteRepository extends MongoRepository<Note, String>` : `Page<Note> findByPatientId(Long, Pageable)` (méthode dérivée, réponse à la contrainte « retrouver un patient depuis ses notes »).
- **Endpoints REST** (périmètre strict des 2 user stories) :
    - `GET /notes?patientId={id}&page={p}&size={s}` → historique paginé (`Page<NoteResponse>`, tri `createdAt DESC` par défaut).
    - `POST /notes` → ajout (`@Valid NoteRequest`), 201.
    - **Pas** de GET-by-id, PUT, DELETE : aucune user story ne les demande.
- DTO in/out + mapper : `NoteRequest` (`@NotNull patientId`, `@NotBlank @Size(max=5000) note`), `NoteResponse`, `NoteMapper` (`@Component`). Le service ne set ni `id` ni `createdAt` (générés par Mongo/auditing).
- `GlobalExceptionHandler` (`@RestControllerAdvice`, `@Slf4j`) : `MethodArgumentNotValidException`→400, corps JSON `{timestamp, status, message, errors}` identique à patient-service. Pas de `NoteNotFound` ni `DuplicateNote` (pas de GET-by-id, pas de dédup — deux notes identiques pour un même patient sont légitimes).
- **Seeding** : `NoteSeeder` (`CommandLineRunner`) charge les 9 notes de test OPC (4 patients, `patientId` 1-4 alignés sur le `data.sql` patient). Idempotent (`count() > 0` → skip), équivalent Mongo du `ON CONFLICT DO NOTHING` SQL. Texte recopié fidèlement (analysé par mot-clé au S3 — toute altération fausserait l'algo). **Spécifique Mongo — ne pas transposer à un `data.sql`.**
- Tests verts : service (mocké), controller (`@WebMvcTest`), filter (unitaire + fail-fast), repository (Testcontainers Mongo réel + auditing).
## gateway-service

- Route `notes-service` ajoutée dans `application.yml` (préfixe train 2025.0 `spring.cloud.gateway.server.webflux.routes`), URI en dur `http://localhost:8083`, predicate `Path=/notes/**`, filter `AddRequestHeader=X-Gateway-Secret,${GATEWAY_SECRET}`.
- **Même secret partagé** que la route patient (réutilisation `${GATEWAY_SECRET}`, pas de nouveau secret). Auth utilisateur (HTTP Basic, in-memory, BCrypt) inchangée.
## frontend-service

- **Pagination bout-en-bout** : le front consomme réellement l'API paginée (pas d'aplatissement), avec navigation Bootstrap sur la page détail.
- `NoteGatewayClient` (`@Component`) : un client par microservice, comme prévu au S1. `findByPatientId(patientId, page)` (taille de page fixe `SIZE=3`, choix d'affichage non configurable — calibré pour rendre la pagination visible en démo) + `create(patientId, note)`. Mécanique d'auth session→Basic répliquée de `PatientGatewayClient` (pas de mutualisation prématurée). `NoteCreateRequest` record privé interne (payload POST, ni DTO de lecture ni form HTML).
- **Lecture/écriture séparées** : `NoteDto` (record) vs `NoteForm` (mutable, `@NotBlank @Size(max=5000)` répliqué du back, message FR). `NotePage` (record, `@JsonIgnoreProperties(ignoreUnknown=true)`) désérialise l'enveloppe Spring `Page` en ne mappant que `content/number/totalPages/first/last` — découple le front de la structure interne de `Page`.
- **Notes dans `PatientController`** (pas de `NoteController` front) : `showDetail` enrichi (charge patient + page de notes + form vide, accepte `?page`), nouveau `POST /patients/{id}/notes` (`addNote`, PRG). Découpage MVC par parcours utilisateur, pas par ressource (voir décisions).
- Template `detail.html` enrichi : section historique (une `card` par note, date formatée `dd/MM/yyyy HH:mm`), navigation pagination Bootstrap (Précédent/Suivant `disabled` sur `first`/`last`, indicateur « Page X / Y »), formulaire d'ajout (`textarea`, blocs `th:errors`). Bootstrap WebJar, zéro CSS custom. Format de date homogénéisé (`#temporals.format`) sur toutes les vues.
- Gestion d'erreur PRG cohérente avec le S1 : `Unauthorized`→login, `NotFound`→liste+flash, échec gateway→message + rechargement. `log.warn` du statut HTTP uniquement, jamais de credential ni de contenu de note (RGPD).
## Sécurité (notes-service, alignée sur patient-service)

Même modèle qu'au S1 : auth utilisateur centralisée à la gateway, provenance vérifiée sur chaque back.

- `SecurityConfig` (`@EnableWebSecurity`) : `csrf disable`, `STATELESS`, `GatewaySecretFilter` avant `UsernamePasswordAuthenticationFilter`, `anyRequest().permitAll()` (c'est le filtre qui bloque, pas une règle d'autorisation Spring — aucun principal utilisateur positionné).
- `GatewaySecretFilter` (`OncePerRequestFilter`, pas `@Component`) : valide `X-Gateway-Secret` en comparaison temps constant (`MessageDigest.isEqual`), 403 si absent/faux.
- **Fail-fast secure-by-default** (nouveauté S2) : le constructeur du filtre lève `IllegalStateException` si le secret est null ou blank → refus de démarrer plutôt que tourner avec une protection silencieusement désactivée. **Rétroporté sur patient-service** (les deux backs portent désormais le même comportement).
- Secrets via `.env` par service (gitignore, `.env.example` versionné), `GATEWAY_SECRET` identique gateway/patient/notes.
- Validé : `:8080` sans auth→401, avec Basic→200 ; `:8083` direct sans secret→403, avec→200.
## Tests (uniformisés Testcontainers sur les deux backs)

Décision structurante du sprint : **abandon de H2, Testcontainers partout** pour tester sur les vrais SGBD (fidélité prod + uniformité de stratégie).

- **notes-service** — 4 classes : `NoteServiceTest` (repository mocké), `NoteControllerTest` (`@WebMvcTest`, Security exclue, `@MockitoBean`), `GatewaySecretFilterTest` (unitaire + cas fail-fast), `NoteRepositoryTest` (`@DataMongoTest` + Testcontainers `mongo:7` + `@Import(MongoAuditingConfig)` pour tester l'auditing).
- **patient-service** — migration H2→Testcontainers PostgreSQL (`postgres:16`) : `PatientServiceApplicationTests` (contexte sur conteneur), nouveau `PatientRepositoryTest` (`@DataJpaTest` + `@AutoConfigureTestDatabase(Replace.NONE)`) testant les requêtes de **déduplication `IgnoreCase` sur le vrai moteur** (angle mort du S1 : les tests service mockaient le repository, la requête SQL elle-même n'était jamais exécutée). H2 retiré du pom.
- Pattern commun : conteneur `static` + `@ServiceConnection` (câblage auto de la datasource/URI, pas de `@DynamicPropertySource`). Les tests d'intégration BDD exigent Docker actif ; les tests unitaires non.
## Décisions d'architecture (justifications soutenance)

1. **Référence pure `patientId` (pas de dénormalisation du nom).** En contexte santé, l'identité patient doit avoir une **source de vérité unique** (patient-service). Dupliquer le nom dans les notes créerait un risque de divergence — une note rattachée à un nom périmé est un problème de sécurité patient, pas cosmétique. Cohérent avec la normalisation 3NF du S1. Trade-off assumé : un appel au patient-service pour afficher le nom, mais le front récupère déjà la fiche patient de toute façon → coût quasi nul. *(À faire valider par le mentor.)*
2. **Justification NoSQL (notes).** Une note médicale est un texte libre non structuré (longueur/contenu imprévisibles) → base orientée document. À l'inverse, le patient est structuré et régulier → SQL + 3NF. Le bon outil pour chaque nature de donnée.
3. **Pagination bout-en-bout.** Décision revue en cours de sprint : la pagination doit descendre jusqu'à la requête base (`limit`/`skip` réels) pour avoir un sens. Paginer côté front seul serait un anti-pattern (transfert complet des données, découpe cosmétique). On pagine donc au back **et** on la consomme au front — cohérence de la base à l'écran.
4. **Sécurité provenance sans Spring Security « utilisateur » sur le back + fail-fast.** L'auth utilisateur reste à la gateway ; le back ne vérifie qu'une provenance (secret partagé). Spring Security est présent (attendu par la grille) mais n'exprime aucune règle d'autorisation utilisateur. Le fail-fast (refus de démarrer sans secret) est une posture secure-by-default.
5. **Front MVC : notes dans `PatientController`.** Le front est du MVC server-side, pas une API REST : le découpage suit les **parcours/pages**, pas les ressources. Les notes vivant sur la page patient, elles relèvent du `PatientController`. Le `NoteController` REST, lui, est côté back où le découpage par ressource s'applique.
6. **Testcontainers partout.** Uniformité + fidélité prod (vrais SGBD, casse `IgnoreCase` validée sur PostgreSQL réel). Arbitrage vs flapdoodle/H2 assumé ; cohérent avec la trajectoire dockerisée du projet.
## Hypothèses posées

- **`createdAt` ajouté** au modèle Note (non demandé explicitement) : justifié par la notion d'**historique** des user stories (« voir l'historique », « d'une séance à l'autre » impliquent une chronologie). Permet le tri anti-chronologique.
- **`@Size(max=5000)` sur `note`** : garde-fou d'intégrité (éviter un document aberrant), pas une contrainte métier. Répliqué côté front pour l'UX.
- **Taille de page fixe (3)** côté front : choix d'affichage, calibré pour rendre la pagination démontrable (patient 4 → 2 pages).
## Points en suspens / écarts assumés

- **Notes orphelines à la suppression d'un patient** : pas de cascade (bases étanches en microservices, pas de FK transverse). Choix assumé — gérer la cascade demanderait du couplage inter-service ou de l'événementiel, hors périmètre. Argument métier renforçant : en santé, on privilégie souvent le *soft delete* (conservation légale du dossier) à la suppression physique. Solution propre si le besoin émerge : pattern événementiel (Saga), que l'archi actuelle permet d'ajouter sans refonte.
- **Affichage `createdAt` en UTC** : les `Instant` sont stockés et affichés en UTC (décalage visible à l'écran). La localisation du fuseau à l'affichage est un raffinement hors périmètre.
- **Rechargement page 0 des notes** en cas d'erreur de validation d'ajout (au lieu de la page courante) : mineur, cohérent avec le comportement de succès (redirect sans param → page 0).
## À reprendre au Sprint 3 (Assessment)

- **Jeu de validation prêt** : les 4 patients seedés (patient-service) + leurs 9 notes (notes-service) sont calibrés par OPC pour produire les 4 niveaux de risque (None/Borderline/In Danger/Early onset) une fois croisés âge/genre × déclencheurs. C'est l'oracle de test de l'algorithme.
- **Recherche des déclencheurs insensible à la casse ET aux accents** : le texte des notes contient accents et apostrophes typographiques (`'`). L'algo devra normaliser (ex : `Normalizer` + `IgnoreCase`) pour matcher « Hémoglobine A1C », « Anormal », etc.
- **Nouveau service `assessment` sans BDD** : interroge patient (âge/genre via la date de naissance) + notes (déclencheurs). Doit porter le `GatewaySecretFilter` (même pattern) + une route gateway `/assessment/**` avec `AddRequestHeader X-Gateway-Secret`. Front : `AssessmentGatewayClient` dédié.
- **Zones d'ambiguïté de l'algo à trancher et documenter** (cf. `CONTEXTE_PROJET_MediLabo.md` §4) : comportement à 1 seul déclencheur, borderline pour un < 30 ans, frontière `> 30` vs `≥ 30`, comptage occurrences uniques vs répétées. Poser des hypothèses explicites.
- **Contrat de calcul de l'âge** : à partir de `birthDate` (champ `birthDate`, D majuscule — contrat patient du S1). Attention au format de renvoi attendu de l'endpoint assessment (point de vigilance OPC).
- **Tests assessment** : mêmes conventions (unitaire logique de scoring + `@WebMvcTest` endpoint). Pas de Testcontainers (service sans BDD), mais possibles mocks des appels aux deux autres services.