# Sprint 4 — Bilan (migration sécurité : secret partagé → JWT RS256)

> **Statut : Sprint 4 terminé, testé end-to-end.**
> **Nature : itération sécurité dédiée, HORS périmètre OPC.** Ce sprint ne répond à aucune user story : c'est une évolution volontaire décidée après clôture du S3, en réponse à une limite du modèle de sécurité identifiée lors des tests end-to-end du S3 et déjà consignée dans le rapport S3 (« Migration sécurité vers JWT — évolution majeure planifiée »).

## La faille qui motive le sprint

Le modèle S1–S3 reposait sur un **secret partagé** (`X-Gateway-Secret`) : la gateway l'injectait sur chaque route, chaque back le validait via un `GatewaySecretFilter`.

Ce mécanisme prouvait la **provenance** (« cette requête vient de la gateway ») mais **pas l'identité** (« quel utilisateur ? »). Conséquence constatée en test S3 : une requête portant le bon secret, **sans aucune authentification utilisateur**, était acceptée par un back appelé en direct. Le secret ne transportait pas l'identité jusqu'aux backs.

**Correction apportée** : un JWT signé RS256, émis par la seule gateway, porte identité **et** provenance en un unique mécanisme cryptographique. Pas de clé privée = pas de token valide = 401.

## Ce qui existe

Aucun nouveau microservice. Les 5 services existants (gateway 8080, patient 8081, front 8082, notes 8083, assessment 8084) sont inchangés fonctionnellement ; seul leur modèle de sécurité change. Stack alignée : Java 21, Spring Boot 3.5.16, Maven mono-repo.

| Composant | Retiré | Ajouté |
|---|---|---|
| **gateway** | `AddRequestHeader=X-Gateway-Secret` (×3 routes) | `JwtIssuer` + `JwtRelayGlobalFilter` (émission) |
| **patient** | `GatewaySecretFilter` + config secret | resource-server (validation JWT) + `authenticated()` |
| **notes** | idem patient | idem patient |
| **assessment** | `GatewaySecretFilter` (entrée) + secret porté en sortie | resource-server (entrée) + **token relay** (sortie) |
| **frontend** | — | **rien : inchangé** |
| commun | `GATEWAY_SECRET` | paire RS256 + `scripts/generate-keys.sh` |

Bilan net : **3 filtres maison supprimés** (et leurs tests), remplacés par un standard Spring + une émission ciblée. Le sprint retire au moins autant de code custom qu'il n'en ajoute.

## Modèle de sécurité (nouveau)

| Segment | Mécanisme |
|---|---|
| navigateur ↔ frontend | Session serveur (`JSESSIONID`) — **inchangé** |
| frontend ↔ gateway | HTTP Basic (creds rejoués depuis la session) — **inchangé** |
| gateway ↔ backs | **JWT RS256 signé** (`Authorization: Bearer`) — remplace le secret partagé |

**Flux nominal** : navigateur → front (session) → gateway (Basic, validé) → gateway forge un JWT signé (clé privée) → back (valide signature + exp + issuer avec la clé publique).

**Flux d'agrégation** : identique jusqu'à assessment, qui **relaie** le token reçu vers patient et notes. Ceux-ci le valident comme s'il venait de la gateway : l'identité de l'utilisateur d'origine (`sub`) traverse intacte toute la chaîne.

## gateway-service (émission)

- **`JwtIssuer`** : charge la clé privée RS256 (PKCS#8 PEM) **une fois au démarrage**, forge un JWT signé RS256 par requête. Claims strictement `sub` (username), `iss` (`medilabo-gateway`), `iat`, `exp`. **Fail-fast** : refus de démarrer si la clé est absente/illisible (posture équivalente au fail-fast de l'ancien secret).
- **`JwtRelayGlobalFilter`** (`GlobalFilter`, `Ordered`) : lit le principal dans le `ReactiveSecurityContextHolder` (contexte **réactif** — la gateway est WebFlux), appelle `JwtIssuer.issue(username)`, mute la requête pour poser `Authorization: Bearer`. Remplaçant dynamique de l'`AddRequestHeader` statique (une valeur constante en YAML ne peut pas porter une identité par requête).
- **Le header `Authorization: Basic` entrant est écrasé** (`headers.set`, pas `add`) : les credentials utilisateur ne franchissent jamais la gateway. Avant le S4, le secret partait vers les backs ; désormais le Basic s'arrête net au point d'authentification.
- `SecurityWebFilterChain` (Basic, in-memory, BCrypt) : **inchangé**. La gateway authentifie toujours de la même façon ; elle atteste désormais par un token.
- Dépendance `nimbus-jose-jwt` déclarée explicitement (utilisée directement, pas seulement transitive).
## patient / notes / assessment (validation)

Pattern identique sur les trois backs (patient a servi de référence) :

- **`spring-boot-starter-oauth2-resource-server`** : validation JWT standard Spring Security, **pas de filtre de validation maison**.
- **`SecurityConfig`** : csrf disable, `STATELESS`, **`anyRequest().authenticated()`** (remplace `permitAll()`), `oauth2ResourceServer(...jwt(decoder))`.
- **Bean `JwtDecoder`** : `NimbusJwtDecoder.withPublicKey(...)` + `setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer))` → valide **signature RS256, expiration ET issuer**. Chargement de la clé publique X.509/SPKI PEM, **fail-fast** si absente.
- Sans token valide → **401** (l'ancien filtre renvoyait 403).
- Suppression complète de `GatewaySecretFilter`, `GatewaySecretFilterTest`, et de toute trace de `gateway.secret` / `GATEWAY_SECRET`.
## assessment-service (double rôle : validation + relay)

Seul back **à la fois serveur et client** : appelé par la gateway (valide un token), il appelle patient et notes en direct (doit présenter un token).

- **`RelayedJwtProvider`** (utilitaire statique) : lit le `SecurityContextHolder` **synchrone** (assessment est servlet, pas réactif), récupère le `Jwt` du principal, retourne `jwt.getTokenValue()` — **la string JWT exacte reçue**, jamais reconstruite ni re-signée (cela romprait la signature de la gateway). Garde explicite : `IllegalStateException` claire si le contexte ne porte pas de JWT, plutôt qu'un NPE/ClassCastException opaque.
- **`PatientClient` / `NoteClient`** : le header `X-Gateway-Secret` est remplacé par `Authorization: Bearer <token relayé>`. Lecture du contexte **explicite dans chaque client** (2 clients seulement → pas d'intercepteur `RestClient` factorisé, YAGNI, cohérent avec la ligne du projet depuis le S1).
- **Base-urls externalisées** en configuration (`patient-service.base-url`, `notes-service.base-url`) — préparation du S5 (noms de services Docker).
## Clés RS256

- **Paire générée par script** : `scripts/generate-keys.sh` produit la clé privée en **PKCS#8 PEM** (format lisible par Nimbus — `openssl genrsa` seul produit du PKCS#1, incompatible : piège de format figé une fois pour toutes dans le script) et la clé publique en **X.509/SPKI PEM** (format du resource-server Spring). Le script place la privée dans gateway-service et **duplique la publique** dans chaque back.
- **Clés de production NON versionnées** (`.gitignore` sur `**/src/main/resources/keys/*.pem`). Une clé privée ne se commit pas, même en démo — cohérence entre la posture du sprint et l'état du dépôt.
- **Clés de test jetables committées** (`src/test/resources/keys/`) : sans valeur de sécurité, nécessaires au build en CI (les backs et la gateway sont fail-fast sur l'absence de clé).
- **Montage projet** : une commande (`./scripts/generate-keys.sh`), puis lancement normal. Documenté au README.
## Décisions d'architecture (justifications soutenance)

1. **RS256 (asymétrique) plutôt que HMAC (symétrique).** Avec HMAC, la gateway et les backs partageraient le **même** secret de signature : chaque back détiendrait de quoi **émettre** un token, pas seulement le valider. On aurait remplacé un secret partagé rejouable par un autre — la faille serait déplacée, pas corrigée. RS256 sépare le **pouvoir d'émettre** (clé privée, gateway seule) du **pouvoir de vérifier** (clé publique, non sensible, distribuée). C'est la seule option qui tient la thèse du sprint. Coût : une paire de clés générée une fois — marginal.
2. **Resource-server standard plutôt qu'un filtre de validation maison.** Le projet utilisait des `OncePerRequestFilter` maison — mais parce qu'aucun standard ne couvrait « valider un secret custom dans un header custom ». JWT/RS256 **est** un standard, avec une implémentation Spring de première classe. Le principe constant du projet n'est pas « toujours du code maison », c'est **« le bon outil pour chaque besoin »** (le même que SQL pour patient / NoSQL pour notes). Écrire sa propre validation JWT exposerait à des failles classiques (`alg:none`, confusion RS256→HS256, expiration non vérifiée) — contradictoire pour un sprint qui durcit la sécurité. Et c'est **moins** de code, pas plus : ~5 lignes de config déclarative contre un filtre à écrire, tester et maintenir.
3. **Front inchangé : Basic conservé sur le segment front→gateway.** La gateway est le point d'authentification : elle vérifie l'identité (Basic), puis l'atteste par un token pour le réseau interne. Faire porter le JWT par le front aurait imposé stockage du token, gestion de l'expiration côté client, refresh tokens — plus de code, plus de surface d'attaque, pour zéro gain. **Conséquence vérifiée en devtools : aucun token ne circule jusqu'au navigateur** (seulement `JSESSIONID`). Un token qui vit 60 s en interne est plus sûr qu'un token stocké dans un navigateur. Zéro ligne modifiée côté front : aucune régression sur du code S1–S3 figé et testé.
4. **Token relay (assessment ne forge jamais).** Assessment retransmet le token reçu à l'identique. Lui donner une clé de signature recréerait exactement la faille corrigée (un back capable de forger une identité). Le relay préserve en outre l'**identité d'origine** à travers l'agrégation : patient et notes voient le `sub` de l'utilisateur réel, pas une identité « assessment ». Propriété système : *seule la gateway est source d'identité*.
5. **Validation de l'`iss` par les backs.** `NimbusJwtDecoder.withPublicKey()` ne valide par défaut que signature et expiration. L'issuer est validé explicitement (`createDefaultWithIssuer`) : défense en profondeur à coût nul (une ligne). **Compatible avec le relay** : le token relayé porte toujours `iss=medilabo-gateway` (celui de la gateway d'origine), donc les trois backs attendent le même issuer, qu'ils soient appelés par la gateway ou par assessment.
6. **Expiration courte (60 s) et configurable.** Le token ne vit qu'un **hop interne** (gateway → back) : il n'a aucune raison de survivre à la requête qui l'a fait naître. Fenêtre de rejeu minimale. Une exp longue « au cas où le front porterait le token un jour » aurait été de la sur-ingénierie (pré-câbler une évolution non décidée). La durée est **configurable** : une éventuelle évolution vers un token porté par le client ne demanderait qu'un changement de config, pas une refonte. Point d'extension identifié, non construit.
7. **`anyRequest().authenticated()` remplace `permitAll()`.** Au S2/S3, Spring Security était présent (attendu par la grille) mais n'exprimait **aucune règle d'autorisation** : c'était le filtre maison qui bloquait, hors chaîne. Désormais la sécurité est portée par la chaîne Spring elle-même : toute requête doit présenter un token valide. Spring Security passe de « présent mais décoratif » à « vrai gardien ».
8. **401 remplace 403.** L'ancien filtre renvoyait 403 (« interdit ») alors qu'aucun utilisateur n'était connu. Le resource-server renvoie **401** (« authentifie-toi »), sémantiquement juste : l'absence de credential valide relève de l'authentification, pas de l'autorisation.
## Hypothèses posées

- **Chemins de clés = configuration, pas secrets.** Les `.env` portent ce qui est sensible ou spécifique à l'environnement **et sans défaut viable** (credentials gateway, coordonnées DB). Un chemin de ressource avec un défaut correct (`classpath:keys/...`) relève de l'`application.properties`. Il reste **surchargeable** via `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` (utile en conteneur au S5), sans imposer une étape de montage.
- **Duplication de la clé publique dans chaque back (assumée).** En microservices, l'**autonomie de déploiement prime sur le DRY**. Un module commun créerait un couplage de build entre 4 services. C'est de la *configuration* dupliquée, pas de la *logique* — cohérent avec la ligne du projet (bases étanches, records locaux, aucune dépendance aux types internes d'autrui). Le script place les fichiers : aucune copie manuelle.
- **Duplication du helper de chargement PEM (~15 lignes, 4 copies).** Même arbitrage : un module partagé pour du parsing PEM stable coûterait plus (couplage de build, redéploiements en cascade) qu'il ne rapporte.
- **Un seul `iss` pour tout le système** (`medilabo-gateway`), y compris pour les appels east-west relayés par assessment : il n'existe qu'un émetteur légitime.
## Limites connues et écarts assumés

- **Un seul utilisateur, aucun rôle.** Le JWT porte l'identité (`sub`) mais **aucune autorisation fine** n'est exprimée (pas de claim `roles`, pas de `@PreAuthorize`). Hors périmètre projet (« pas d'inscription, pas de gestion de droits/rôles »). Le mécanisme le permettrait sans refonte : le point d'extension existe, il n'est pas construit.
- **Basic non chiffré sur le segment front→gateway.** Le HTTP Basic transmet les credentials en Base64 (pas chiffré) à chaque requête : sans TLS, ils sont lisibles sur le réseau. Périmètre projet (démo locale, pas de TLS demandé) ; en production ce segment serait en HTTPS, auquel cas Basic + TLS est un mécanisme légitime. Le S4 garantit au moins que ces credentials **ne franchissent jamais la gateway**.
- **Coût crypto par requête (assumé, mesuré).** Chaque requête entraîne une signature RSA (gateway, ~1 ms) et une vérification par back (~0,05–0,1 ms — la vérification RSA est 10 à 20× plus rapide que la signature). Une page détail patient ≈ 3 signatures + 5 vérifications ≈ 2–3 ms, face à 5 appels HTTP et 2 requêtes base : **1 à 2 % du temps de réponse**. Pas de cache de token (un token mis en cache serait un token dont la fraîcheur d'identité n'est plus garantie), pas d'algorithme alternatif (EdDSA) : l'optimisation ne se justifie pas à cette échelle. **À reprendre au S6 (Green Code)** comme point d'analyse concret.
- **Expiration vs latence d'agrégation.** Le token relayé par assessment traverse deux appels HTTP en série (dont une boucle de pagination). En local, quelques dizaines de millisecondes — très loin des 60 s. Spring tolère en outre un `clock-skew` par défaut. Aucun risque réel ; à revérifier si la chaîne s'allongeait.
- **Pas de rotation de clés, pas de refresh token, pas de JWKS endpoint, pas de révocation.** Écartés délibérément : JWT est un domaine où l'on sur-conçoit vite. Le périmètre est *émettre / signer / valider*.
- **Notes orphelines, couplage de disponibilité d'assessment, rechargement page 0** : limites héritées du S2/S3, **inchangées** par ce sprint.
## Tests

Conventions projet (JUnit 5, AssertJ, Mockito, nommage `should_X_when_Y`).

- **`JwtIssuerTest`** (gateway) : token signé validable avec la clé publique, claims `sub`/`iss`/`iat`/`exp` corrects, `exp - iat` = durée configurée, signature rejetée avec une autre clé publique, fail-fast si clé absente.
- **`SecurityConfigTest`** (×3 backs, squelette identique) : **remplace conceptuellement `GatewaySecretFilterTest`**. 5 cas prouvant le contrat de sécurité — sans header → 401, JWT valide → 200, signé avec une autre clé → 401, **mauvais issuer → 401**, expiré → 401. `@WebMvcTest` **avec** Security importée (contrairement aux tests de controller).
- **`PatientClientTest` / `NoteClientTest`** (assessment) : **preuve de la propriété centrale du sprint**. Un `Jwt` de `tokenValue` connu est posé dans le `SecurityContext` ; on vérifie que le header sortant porte **exactement** `Bearer <ce tokenValue>` (`MockRestServiceServer`). Assessment relaie, il ne forge pas.
- **`RelayedJwtProviderTest`** : contexte avec `Jwt` → tokenValue ; contexte vide → `IllegalStateException` ; principal non-`Jwt` → `IllegalStateException`.
- **Inchangés** : les `@WebMvcTest` de controller continuent d'**exclure** la Security (ils testent mapping/JSON/statuts métier, pas l'authentification — séparation des préoccupations). Tests métier (service, repository Testcontainers, `RiskCalculator`, `TriggerDetector`) : aucune modification.
- **Clés de test** : chaque service embarque une paire jetable committée dans `src/test/resources/keys/`. Les `@TestPropertySource` pointent `jwt.public-key-path` dessus (la clé de prod est gitignorée, absente en CI).
## Validation end-to-end

- `:8080/patients` sans credentials → **401** ; avec Basic → **200**.
- `:8081` / `:8083` / `:8084` en direct sans JWT → **401** ; avec JWT valide → **200**.
- Page détail patient : patient + notes + **évaluation du risque** affichés (chaîne complète front → gateway → assessment → patient + notes).
- Devtools navigateur : **aucun JWT** visible, seulement `JSESSIONID`.
- Grep repo : **zéro référence fonctionnelle** à `X-Gateway-Secret` / `GATEWAY_SECRET` (subsistent uniquement les rapports historiques S1–S3 et deux commentaires documentant la migration).
> ⚠️ **Piège de test** : le navigateur **rejoue automatiquement** les credentials Basic une fois saisis (cache par domaine). Toute vérification de sécurité doit passer par `curl` ou une fenêtre de navigation privée — sinon on teste le cache, pas le code. À retenir pour la démo devant le jury.

## À reprendre

- **Sprint 5 — Dockerisation.** Les clés sont chargées en `classpath:` (embarquées au build dans le jar) : elles suivront naturellement en conteneur. Les chemins restent surchargeables si un montage par volume est préféré. **Point de vigilance** : les base-urls d'assessment (`patient-service.base-url` / `notes-service.base-url`, aujourd'hui `localhost:8081/8083`) devront pointer vers les **noms de services Docker**. Ajouter Actuator uniformément (healthchecks compose).
- **Sprint 6 — Green Code.** Le coût crypto par requête (signature RSA à chaque appel, non mise en cache) est un point d'analyse concret, chiffrable, avec des pistes identifiées **et volontairement non implémentées** (cache de token, EdDSA) — savoir ne *pas* optimiser à une échelle donnée fait partie de l'analyse.
- **Évolutions écartées, points d'extension identifiés** : autorisation par rôles (le JWT porte déjà l'identité), token porté par le front (durée d'expiration déjà configurable), TLS sur le segment front→gateway. Aucune ne demande de refonte du modèle.