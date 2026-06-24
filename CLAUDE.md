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
| `gateway-service` | Point d'entrée unique | — |
| `frontend-service` | Interface sobre, indépendante du back | — |

---

## Stack (imposée)

Java 21 · Spring Boot 4.x (le `pom.xml` de chaque service fait foi) · Maven (`./mvnw`) · PostgreSQL via Spring Data JPA · MongoDB via Spring Data MongoDB · Spring Cloud Gateway · Spring Security · Lombok · Docker Compose.

> ⚠️ **Spring Boot 4.x est récent (baseline Spring Framework 7).** La plupart des tutos/réponses en ligne visent Spring Boot 3.x, et des API/configs dépréciées en 3.x ont été supprimées en 4.x (la config Spring Security notamment). Ne pas appliquer aveuglément des patterns 3.x : en cas de doute, privilégier la doc Spring Framework 7 / Spring Boot 4 et vérifier la compatibilité.

---

## Rôle de Claude Code

Implémenteur, pas décideur. Les choix d'architecture, de design et de trade-offs se font côté Claude Web. Face à une ambiguïté architecturale → poser la question, ne pas trancher seul. Avant de coder une tâche non triviale : exposer en quelques lignes l'approche retenue, puis le code, puis la validation à demander.

---

## Architecture & conventions

### Structure en couches (back), par service

```
controller/   → endpoints REST, reçoit/renvoie des DTO
service/      → logique métier
repository/   → accès données (Spring Data)
model/        → entités (JPA pour patient, documents pour notes)
dto/          → objets exposés par l'API
mapper/       → mapping entité ↔ DTO
```

### Conventions

- Code en anglais (classes, méthodes, variables, packages) ; commentaires et doc en français.
- Packages : `com.medilabo.<service>`.
- **DTO obligatoires** : ne jamais exposer les entités directement. Mapping via la couche `mapper`.
- Java : PascalCase (classes), camelCase (méthodes/variables), UPPER_SNAKE_CASE (constantes). DB : snake_case. REST : kebab-case, versionné `/api/v1/`.
- Javadoc sur l'API publique. Commentaires inline sur le *pourquoi* non-évident, pas sur les getters/setters/constructeurs triviaux.
- Lombok pour le boilerplate (getters/setters, constructeurs, `@Slf4j`), sans masquer de logique.

### Pragmatisme (important en contexte d'évaluation)

SOLID = boussole, pas dogme. Chaque abstraction doit se justifier par un besoin réel du périmètre : pas d'interface de service « par principe », pas de généricité spéculative, pas de couche supplémentaire (façade, use-cases) au-delà de `controller/service/repository`.

---

## Périmètre & garde-fous

- **Périmètre figé, critère d'évaluation explicite.** Ne jamais ajouter de fonctionnalité non demandée. En particulier NE PAS implémenter : inscription/registration, gestion de rôles/droits, front élaboré, endpoints ou entités non prévus.
- Maintenabilité ≠ sur-ingénierie : couches propres, contrats d'API clairs, faible couplage, sans anticiper de besoins hors périmètre.
- Toute demande ou implémentation qui semble sortir du périmètre → le signaler plutôt que l'exécuter silencieusement.

---

## Tests

- TDD privilégié quand c'est pragmatique, sans rigidité. Base anti-régression attendue (CI à venir).
- Nommage explicite : `should_<expected>_when_<condition>`.
- Distinguer **unit** (logique métier : services, mappers, surtout l'algorithme d'assessment) et **integration** (endpoints, quand pertinent).
- Prioriser ce qui protège réellement contre la régression, pas la couverture exhaustive.

---

## Sécurité

- Spring Security sur chaque microservice back. Authentification HTTP basic, utilisateurs in-memory, mots de passe encodés BCrypt (obligatoire). Pas d'inscription, pas de rôles/droits.
- **Secrets** : aucun secret, mot de passe ou credential en dur dans le code ou les fichiers versionnés. Credentials DB (Postgres, Mongo) et config sensible via variables d'environnement, `.env` dans `.gitignore`. La config Docker Compose lit ces variables, ne les code pas en dur.
- **Validation des entrées multi-couches** : Bean Validation sur les DTOs (web), règles métier en Java pur (assessment), préconditions (existence/état) dans la couche service. Chaque couche se protège, pas de confiance aveugle dans la précédente.
- Config Spring Security : doc à jour Spring Boot 4 (cf. avertissement stack), ne pas copier une config 3.x.

---

## Gestion d'erreurs

- Exceptions métier explicites et nommées (ex. `PatientNotFoundException`), jamais de `RuntimeException` générique. Définies dans un package `exception/` du service, ou dans la couche service.
- Levées dans la couche service quand une règle ou une précondition n'est pas respectée (ex. patient inexistant).
- Traduites en réponses HTTP via un `@RestControllerAdvice` (handler centralisé au niveau controller), qui mappe chaque exception métier vers le bon status.
- Réponse d'erreur API cohérente : status, error code, message, timestamp, path.

---

## Logging

- SLF4J via Lombok `@Slf4j`, pas de `System.out.println`. Logs structurés sur les événements métier significatifs, niveaux différenciés par profil (dev verbeux, prod sobre).
- Aucune donnée patient ni secret dans les logs (données de santé — wording générique, pas de PII, pas de payload sensible).

---

## Algorithme d'assessment (cœur métier)

Pièce la plus importante et la plus à risque de régression. Règles complètes dans `docs/features/assessment.md`.

- Incorporer toutes les règles (4 niveaux : None, Borderline, In Danger, Early onset).
- Recherche des termes déclencheurs **insensible à la casse** dans les notes.
- Respecter le **format de renvoi attendu** par l'endpoint.
- Zones d'ambiguïté → hypothèses explicites documentées dans le fichier feature, ne pas trancher silencieusement.
- Testé unitairement de façon poussée.

---

## Git & dépendances

- Commits et push gérés par le développeur. À chaque palier cohérent, **proposer un message de commit sans committer**. Convention : `feat`, `fix`, `chore`, `refactor`, `test`, `docs`. Format court (`feat(patient): add patient CRUD endpoints`). Granularité : un commit = une unité logique cohérente (historique propre = critère d'évaluation).
- **Aucun ajout de dépendance sans validation explicite.** Si une lib semble nécessaire (ex. contourner une API Spring Boot 4 supprimée), l'évoquer et attendre — ne pas l'ajouter.

---

## Documentation feature

Toute feature documentée dans `docs/features/<feature>.md` : résumé, endpoints, architecture impactée, décisions/hypothèses, comment tester, limitations.

---

## Découpage par sprints

Patient (S1) → Notes (S2) → Assessment (S3), puis Docker et Green Code. Chaque service back est généré **au moment de son sprint**. Ne pas scaffolder de service non encore abordé.