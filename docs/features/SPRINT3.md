# Sprint 3 — Bilan (assessment-service, + évolutions gateway/frontend)

> **Statut : Sprint 3 terminé, testé end-to-end sur l'oracle des 4 patients.**

## Ce qui existe

Un nouveau microservice back **assessment-service** (port 8084), **sans base de données**, qui calcule le niveau de risque de diabète d'un patient en croisant son âge/genre (patient-service) et les déclencheurs présents dans ses notes (notes-service). Plus l'affichage du risque (badge coloré + déclencheurs) sur la page détail patient côté front, et la route gateway associée. Stack alignée sur les sprints précédents : Java 21, Spring Boot 3.5.16, Maven mono-repo (`medilabo-solutions`).

- **assessment-service** (8084) — calcul du risque, stateless, sans persistance. Interroge patient et notes **en direct** (service-to-service).
- **gateway-service** (8080) — route `/assessment/**` ajoutée, injection du secret de provenance.
- **frontend-service** (8082) — page détail patient enrichie : section « Évaluation du risque de diabète » (badge + déclencheurs).

Ports : gateway 8080, patient 8081, front 8082, notes 8083, **assessment 8084**.

## assessment-service

- **Aucune BDD**, service stateless : le risque est recalculé à chaque appel (fonction pure de l'état courant âge + notes). Aucun cache, aucune persistance — cohérent avec la contrainte projet (Assessment sans base) et correct métier (une note ajoutée doit refléter le risque immédiatement).
- **Endpoint unique** : `GET /assessment/{patientId}` → JSON `{patientId, riskLevel, triggers}`. Aucun autre endpoint (pas de CRUD, périmètre strict de la seule user story).
- **Appels service-to-service directs** vers patient-service (`http://localhost:8081`) et notes-service (`http://localhost:8083`), via `RestClient` (convention projet). Assessment porte le header `X-Gateway-Secret` sur ses appels sortants pour franchir le `GatewaySecretFilter` des deux backs.
- `PatientClient` : `GET /patients/{id}`, mappe un `PatientResponse` (record local, copie du contrat patient — `birthDate` D majuscule, `gender` String, pas de dépendance à l'enum `Gender` interne de patient-service). Traduit un 404 en `PatientNotFoundException`, re-throw les autres erreurs telles quelles.
- `NoteClient` : `GET /notes?patientId={id}&page={p}&size={s}`, récupère **toutes** les notes du patient par pagination en boucle (`do/while` jusqu'à `last`, taille 50) pour garantir l'exhaustivité de la détection. `NotePage` (record, `@JsonIgnoreProperties(ignoreUnknown=true)`) désérialise l'enveloppe Spring `Page` en ne mappant que `content/number/totalPages/first/last`.
- `AssessmentService` : orchestration — appelle patient, garde birthDate, appelle notes, calcule l'âge, détecte les déclencheurs, score, assemble la réponse. L'ordre des appels (patient **puis** notes) court-circuite l'appel notes si le patient est introuvable.
- `AssessmentResponse` (record) : `{patientId, riskLevel (String lisible), triggers (List<String>)}`. **Ne contient PAS** firstName/age/gender — pas de duplication de la fiche patient (le front l'a déjà), cohérent avec le principe de référence pure du S2.
- `GlobalExceptionHandler` (`@RestControllerAdvice`, `@Slf4j`) : `PatientNotFoundException`→404, `InvalidPatientDataException`→502, corps JSON `{timestamp, status, message}` identique aux autres backs.
- Logging : `log.warn` du statut HTTP sur échec d'appel patient/notes. **Jamais de donnée patient ni de note** (RGPD).

### Algorithme d'évaluation (cœur métier)

Deux composants purs, testables en isolation :

- **`RiskCalculator`** (`final`, méthode statique, sans Spring) : table de décision `(âge, genre, nombre de déclencheurs) → RiskLevel`. Ordre d'évaluation du plus grave au moins grave (Early onset → In Danger → Borderline → None).
- **`TriggerDetector`** (`@Component`) : détection des déclencheurs distincts. Normalisation (minuscules + suppression des accents via `Normalizer` NFD + `\p{M}`) appliquée au texte des notes **et** aux termes recherchés ; matching en `contains`. Table de synonymes en `LinkedHashMap` (ordre de sortie stable).
- **`RiskLevel`** (enum) : `NONE/BORDERLINE/IN_DANGER/EARLY_ONSET`, chacun porteur d'un label lisible (« None », « Borderline », « In Danger », « Early onset ») renvoyé dans le JSON.

Table de synonymes (déclencheur canonique → termes normalisés cherchés) :

| Déclencheur | Termes recherchés |
|---|---|
| Hémoglobine A1C | `hemoglobine a1c`, `hba1c` |
| Microalbumine | `microalbumine` |
| Taille | `taille` |
| Poids | `poids` |
| Fumeur | `fumeur`, `fumeuse`, `fume` |
| Anormal | `anormal` |
| Cholestérol | `cholesterol` |
| Vertiges | `vertige` |
| Rechute | `rechute` |
| Réaction | `reaction` |
| Anticorps | `anticorps` |

Table de décision effective :

| Population | Nombre de déclencheurs distincts | Niveau |
|---|---|---|
| < 30 ans, homme | 0–2 / 3–4 / ≥5 | None / In Danger / Early onset |
| < 30 ans, femme | 0–3 / 4–6 / ≥7 | None / In Danger / Early onset |
| ≥ 30 ans (indépendant du genre) | 0–1 / 2–5 / 6–7 / ≥8 | None / Borderline / In Danger / Early onset |

## gateway-service

- Route `assessment-service` ajoutée dans `application.yml` (préfixe train 2025.0 `spring.cloud.gateway.server.webflux.routes`), URI en dur `http://localhost:8084`, predicate `Path=/assessment/**`, filter `AddRequestHeader=X-Gateway-Secret,${GATEWAY_SECRET}`.
- **Même secret partagé** que les routes patient/notes (réutilisation `${GATEWAY_SECRET}`). Auth utilisateur (HTTP Basic, in-memory, BCrypt) inchangée.

## frontend-service

- `AssessmentGatewayClient` (`@Component`) : un client par microservice, comme prévu depuis le S1. `findByPatientId(patientId)` → `GET /assessment/{id}` via la gateway (port 8080, jamais assessment-service en direct). Mécanique d'auth session→Basic répliquée des clients existants (pas de mutualisation prématurée). `AssessmentDto` (record) de lecture seule.
- **Assessment traité comme un enrichissement dégradable de la page détail.** `loadAssessment` : `Unauthorized` re-thrown (→ redirect login comme partout), toute autre erreur (`RestClientResponseException` 4xx/5xx, `RestClientException` service injoignable) → `null` + `log.warn` du statut. **Une panne d'assessment-service n'empêche pas l'affichage du patient et de ses notes.**
- **Notes dans `PatientController`** (découpage front par parcours/page, décision S2) : `showDetail` charge patient + notes + assessment. Le montage des attributs communs (patient, notePage, assessment) est factorisé dans `populateDetailModel`, utilisé par `showDetail` **et** `reloadDetailWithForm` — l'assessment reste donc affiché même quand la validation du formulaire d'ajout de note échoue (le `noteForm`, lui, reste géré séparément par chaque chemin pour préserver la saisie invalide).
- **Affichage sur la page détail uniquement, pas dans la liste des patients** : mapping badge fait côté front à partir du `riskLevel`. None → gris (`bg-secondary`), Borderline → jaune (`bg-warning`), In Danger → orange (style inline `#fd7e14`, l'orange Bootstrap, seul écart au « zéro CSS custom » — une couleur métier, pas du theming), Early onset → rouge (`bg-danger`). Déclencheurs affichés en petits badges sous le niveau, ou « Aucun déclencheur détecté » si vide ; « Évaluation indisponible » si l'appel a échoué.

## Sécurité (assessment-service, alignée sur patient/notes)

Même modèle qu'aux sprints précédents : auth utilisateur centralisée à la gateway, provenance vérifiée sur chaque back.

- `GatewaySecretFilter` (`OncePerRequestFilter`, pas `@Component`) et `SecurityConfig` (`@EnableWebSecurity`, `csrf disable`, `STATELESS`, filtre avant `UsernamePasswordAuthenticationFilter`, `anyRequest().permitAll()`) répliqués à l'identique de patient-service. Fail-fast au constructeur (refus de démarrer si secret null/blank), comparaison temps constant (`MessageDigest.isEqual`), 403 si header absent/faux.
- Le secret `${GATEWAY_SECRET}` (identique gateway/patient/notes/assessment) sert **deux rôles** dans assessment : validé en entrée par le filtre (appel via gateway), et porté en sortie vers patient/notes (appels service-to-service). Config via `.env` par service.
- Validé end-to-end : `:8084` direct sans secret → 403, avec secret → 200 ; via gateway sans auth utilisateur → 401.

## Tests

Conventions projet (JUnit 5, AssertJ, Mockito, nommage `should_X_When_Y`). Pas de Testcontainers (service sans BDD).

- **`RiskCalculatorTest`** : table de vérité complète en `@ParameterizedTest`/`@CsvSource` (< 30 ans par genre, ≥ 30 ans), frontière d'âge 30, cas contre-intuitifs nommés (Borderline réservé aux > 30, monotonie du risque), insensibilité à la casse du genre, les 4 patients de l'oracle.
- **`TriggerDetectorTest`** : les 4 patients de l'oracle (détection + test bout-en-bout enchaînant `detect` → `calculate` pour figer les 4 niveaux attendus), comptage distinct (fume/fumeur = 1, anormal répété = 1), normalisation (casse, accents, Vertige/Vertiges, apostrophe typographique U+2019), cas vides/null, ordre de sortie stable, absence de faux positifs Taille/Poids sur le patient 3.
- **`AssessmentServiceTest`** : calcul de l'âge avec dates **relatives à `now()`** (frontière 29/30/31), orchestration (patient sans notes → None), garde birthDate null, propagation `PatientNotFoundException` (notes jamais appelé).
- **`AssessmentControllerTest`** (`@WebMvcTest`, Security exclue, `@MockitoBean`) : format JSON 200, cas None + triggers vide, propagation 404, mapping 502 (birthDate null).
- **`GatewaySecretFilterTest`** : header absent/faux/correct + fail-fast constructeur.
- **`PatientControllerTest`** (front, première couverture de test du frontend-service) : assessment placé dans le Model si succès, page rendue avec assessment null si l'appel échoue (dégradation propre), redirect login si Unauthorized, assessment conservé si la validation de note échoue.

## Décisions d'architecture (justifications soutenance)

1. **Appels service-to-service directs (pas via la gateway).** Assessment interroge patient et notes en direct, sans passer par la gateway. Distinction **north-south / east-west** : la gateway gère le trafic entrant (client → système, north-south : authentification utilisateur, point d'entrée) ; le trafic interne service-à-service (east-west) n'a pas de raison de remonter au point d'entrée public. Router l'east-west par la gateway ajouterait de la latence (double hop), en ferait un goulot et un point de défaillance unique pour la communication interne. Trade-off assumé : le header s'appelle `X-Gateway-Secret` alors qu'assessment le porte aussi — c'est en réalité un secret de **confiance intra-cluster** hérité du nom du S1, non renommé pour ne pas churner du code figé.

2. **Couplage d'agrégation faible et voulu.** Assessment dépend de patient et notes — c'est sa raison d'être (service d'agrégation). Couplage **par contrat REST** (pas par implémentation : records locaux, `gender` en String, aucune dépendance aux types internes des autres services), **unidirectionnel** (patient/notes ignorent Assessment), sans cycle. À distinguer d'un couplage ajouté sans nécessité (cf. validation `patientId` refusée, écarts en suspens).

3. **Service stateless sans BDD.** Contrainte projet + correction métier : le risque est une fonction pure de l'état courant. Aucun cache (un résultat mis en cache serait périmé dès qu'une note change — dangereux en santé). Recalcul à chaque appel.

4. **Format de renvoi JSON `{patientId, riskLevel, triggers}`.** `riskLevel` en libellé lisible (respect du format de renvoi attendu, point de vigilance OPC). `triggers` (noms canoniques des déclencheurs trouvés) exposé pour l'affichage détaillé. Pas de firstName/age dans la réponse : pas de duplication de la fiche patient.

5. **Affichage détail uniquement (pas de colonne risque dans la liste).** Afficher le risque dans la liste des patients aurait imposé N appels assessment (chacun = 2 appels internes) au chargement — anti-pattern réseau (N+1 à l'échelle HTTP), incompatible avec l'esprit Green Code, et hors user story (« consulter le risque **pour un patient** », page détail). Un seul appel, à l'ouverture d'un patient.

6. **Mapping couleur côté front.** Le back renvoie la donnée métier brute (libellé) ; le front décide de sa présentation (badge coloré). Séparation des responsabilités.

7. **Garde birthDate null → 502 Bad Gateway.** Un `birthDate` null viole le contrat `@NotNull` de patient-service : donnée amont corrompue. Échec explicite (`InvalidPatientDataException`) plutôt que NPE opaque. 502 (et non 400/500) car la faute vient d'une dépendance amont ayant renvoyé une donnée hors contrat, pas du client ni d'assessment.

8. **Assessment dégradable côté front.** L'évaluation est un enrichissement de la page détail, pas son cœur : une panne d'assessment-service laisse la fiche patient et les notes consultables (« Évaluation indisponible »). Robustesse démontrée end-to-end (test 6).

## Hypothèses posées (zones d'ambiguïté de l'algorithme, cf. `CONTEXTE_PROJET_MediLabo.md` §4)

Les règles OPC comportent des trous de spécification, tranchés par hypothèses explicites, validées contre l'oracle des 4 patients :

- **Comptage : déclencheurs distincts, une occurrence suffit.** Un terme présent une ou plusieurs fois compte 1. Les variantes d'un même déclencheur comptent 1 (« fume » + « fumeur » = Fumeur, une fois). Validé de façon discriminante par le patient 3 (« fume depuis peu » + « il est fumeur » = 1 seul Fumeur → 3 déclencheurs → In Danger, conforme à l'oracle ; un comptage par occurrences aurait faussé le niveau).
- **Frontière d'âge : « plus de 30 ans » interprété `≥ 30`.** Le texte OPC ne définit rien pour exactement 30 ans (« plus de 30 » et « moins de 30 » stricts laissent un trou). Décision assumée : 30 ans pile applique le régime ≥ 30.
- **Borderline réservé aux ≥ 30 ans.** Le niveau Borderline n'est défini par OPC que pour les > 30 ans. Un patient < 30 ans sous les seuils In Danger/Early onset de sa catégorie retombe en **None** (décision : pas de risque si aucun critère explicite rempli). Conséquence contre-intuitive assumée et testée : un homme de 25 ans avec 2 déclencheurs = None, alors qu'un homme de 35 ans avec 2 déclencheurs = Borderline.
- **Fallback = None.** Tout patient ne remplissant aucun critère (dont le cas à 1 seul déclencheur) → None.
- **Monotonie du risque (hypothèse ajoutée, non couverte par l'oracle).** Les règles OPC littérales pour les < 30 ans (« homme : 3 déclencheurs → In Danger », « femme : 4 ») créent une non-monotonie absurde : un homme à 4 déclencheurs, ou une femme à 5–6, ne remplirait aucune règle et retomberait en None malgré **plus** de facteurs de risque. Hypothèse posée : le risque est monotone (plus de déclencheurs ne peut pas faire baisser le niveau). Implémenté en `>=` (homme < 30 : ≥ 3 → In Danger, ≥ 5 → Early onset ; femme < 30 : ≥ 4 → In Danger, ≥ 7 → Early onset). **Ces cas ne sont pas couverts par l'oracle des 4 patients** (aucun ne tombe dessus) : c'est un choix documenté, figé par des tests nommés, pas une correction validée par les données.
- **Table de synonymes.** « Fumeur » = liste exacte `fumeur/fumeuse/fume` (le radical `fum` a été **écarté volontairement** : risque de faux positifs médicalement dangereux comme « parfum »/« fumée » dans un outil de santé). « Hémoglobine A1C » enrichi de l'abréviation universelle `hba1c` (même réalité clinique) ; « hémoglobine glyquée » écarté (hors terminologie OPC). « Vertiges » cherché au singulier `vertige` pour matcher les deux formes.

## Limites connues et écarts assumés

- **Comptage lexical, pas sémantique (limite de conception médicale).** L'algorithme compte la présence de termes, sans comprendre leur sens dans la phrase. Ainsi « Poids égal ou inférieur au poids recommandé » (poids **normal**, non pathologique) fait compter le déclencheur « Poids » comme un facteur de risque. Isolé, l'impact est nul (1 déclencheur reste None). Mais **cumulé à un vrai facteur** (ex : HbA1c élevée), ce faux signal peut faire basculer artificiellement un patient en Borderline — incohérence médicale réelle. C'est une limite intrinsèque de la méthode spécifiée par le client (comptage de mots-clés) : la lever demanderait du NLP capable de gérer la négation et le contexte, hors périmètre. La corriger par des règles ad hoc s'écarterait de la terminologie fournie et de l'oracle.
- **Provenance ≠ identité (limite du modèle de sécurité).** Les backs ne vérifient qu'une **provenance** (secret partagé), l'identité de l'utilisateur restant à la gateway. Conséquence : une requête portant le bon secret **sans** auth utilisateur est acceptée par un back appelé en direct (le back fait confiance à la provenance interne). C'est le trust model assumé (auth faite une fois à la porte d'entrée, réseau interne de confiance), cohérent avec le périmètre (pas de gestion de rôles par utilisateur). Le secret ne transporte pas l'identité jusqu'aux backs. **Évolution majeure planifiée** : remplacer le secret partagé par une propagation de **JWT signé** émis par la gateway, portant à la fois provenance et identité (voir « À reprendre »).
- **Notes orphelines (hérité S2, non bloquant).** `patientId` n'est pas validé à la création d'une note (bases étanches, pas de FK inter-service). Une note peut donc théoriquement pointer un patient inexistant. **Sans impact sur l'assessment** : celui-ci part toujours du patient (404 si absent avant même de lire les notes), donc une note orpheline n'est jamais évaluée. Correctif propre = pattern événementiel, hors périmètre.
- **Assessment couplé en disponibilité.** Agrégateur synchrone : si patient ou notes est indisponible, l'assessment échoue (dégradé proprement côté front). Pas de circuit breaker (Resilience4j) — hors périmètre à cette échelle.
- **Rechargement page 0 des notes sur échec de validation d'ajout** (hérité S2) : le rechargement de la page détail après une note invalide repart en page 0 des notes. Mineur, comportement inchangé au S3.

## À reprendre (sprints suivants & évolutions)

- **Migration sécurité vers JWT (évolution majeure planifiée).** Remplacer le secret partagé `X-Gateway-Secret` par une propagation de JWT signé : la gateway authentifie l'utilisateur puis émet un token signé ; chaque back valide la signature (identité + provenance en un seul mécanisme cryptographique). Refonte du modèle de sécurité (supprime le secret partagé), à traiter comme une itération dédiée après clôture du S3.
- **Sprint 5 — Dockerisation** : un Dockerfile par microservice (dont assessment) + un `docker-compose.yml` global. Vigilance sur la cohérence des noms de services (les URIs en dur `localhost:8081/8083/8084` d'assessment devront pointer vers les noms de services Docker). C'est aussi le moment d'ajouter Actuator uniformément sur tous les backs (healthchecks compose) — volontairement non introduit en solo au S3 pour ne pas créer d'asymétrie.
- **Sprint 6 — Green Code** : section README (analyse + pistes de refactoring localisées, au niveau d'exigence de la grille). Le service assessment stateless et le comptage lexical sont des points concrets à analyser.

## Jeu de validation (oracle OPC)

Les 4 patients seedés (patient-service) + leurs 9 notes (notes-service) produisent, croisés âge/genre × déclencheurs, les 4 niveaux de risque. Résultats validés end-to-end via le navigateur et l'endpoint :

| Patient | Âge / genre | Déclencheurs détectés | Niveau |
|---|---|---|---|
| 1 — TestNone | ≥ 30, F | Poids (1) | **None** |
| 2 — TestBorderline | ≥ 30, M | Anormal, Réaction (2) | **Borderline** |
| 3 — TestInDanger | < 30, M | Fumeur, Anormal, Cholestérol (3) | **In Danger** |
| 4 — TestEarlyOnset | < 30, F | Hémoglobine A1C, Taille, Poids, Fumeur, Cholestérol, Vertiges, Réaction, Anticorps (8) | **Early onset** |