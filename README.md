# MédiLabo Solutions

Application d'aide au dépistage du risque de diabète de type 2, développée pour la clinique Abernathy. Mono-repo de 5 microservices Spring Boot déployés ensemble.

Voir `CLAUDE.md` pour le cadrage complet (stack, conventions, sécurité, découpage par sprints) et `docs/features/` pour la documentation détaillée de chaque fonctionnalité.

## Lancement de l'application

Prérequis unique : **Docker Desktop**. Ni Java, ni Maven, ni PostgreSQL, ni MongoDB à installer. Tout tourne en conteneurs.

```bash
./scripts/generate-keys.sh
```

```bash
docker compose up --build
```

Puis ouvrir **http://localhost:8082** et se connecter avec :

- **Identifiant** : `user`
- **Mot de passe** : `medilabo`

### Pourquoi `generate-keys.sh` d'abord

La sécurité interne (gateway → services back) repose sur un JWT signé en **RS256**. La clé privée de signature ne se commit jamais dans un dépôt Git, même en contexte de démonstration, chaque environnement doit générer la sienne. Cette commande génère localement la paire de clés (non versionnée, ignorée par Git) et la place aux emplacements attendus par la gateway et les 3 services back. Le build Docker les embarque ensuite dans les images. Sans cette étape, les conteneurs refusent de démarrer (fail-fast, clé absente).

### Premier `up --build`

Chaque service se construit dans son propre conteneur (build Maven multi-stage inclus) : la première exécution télécharge les dépendances Maven et les images de base pour les 5 modules, ce qui peut prendre plusieurs minutes selon la connexion. `docker compose logs -f` permet de suivre la progression si besoin.

## Vérification rapide

Le seeding au démarrage crée 4 patients de test, chacun conçu pour illustrer un niveau de risque distinct de l'algorithme d'évaluation :

| Patient | Âge / genre | Déclencheurs détectés | Niveau attendu |
|---|---|---|---|
| TestNone | ≥ 30 ans, F | Poids (1) | **None** |
| TestBorderline | ≥ 30 ans, M | Anormal, Réaction (2) | **Borderline** |
| TestInDanger | < 30 ans, M | Fumeur, Anormal, Cholestérol (3) | **In Danger** |
| TestEarlyOnset | < 30 ans, F | Hémoglobine A1C, Taille, Poids, Fumeur, Cholestérol, Vertiges, Réaction, Anticorps (8) | **Early onset** |

Détail des règles de calcul : `docs/features/6. assessment.md`.

**Parcours de vérification** : se connecter → la liste affiche les 4 patients → cliquer sur un patient → la page détail affiche ses informations, l'historique paginé de ses notes, et un badge d'évaluation du risque (avec la liste des déclencheurs détectés) cohérent avec le tableau ci-dessus.

## Architecture

| Service | Port | Rôle | Base de données |
|---|---|---|---|
| `gateway-service` | 8080 | Point d'entrée unique, authentification HTTP Basic, émission du JWT interne | — |
| `patient-service` | 8081 | Données démographiques patient (CRUD) | PostgreSQL |
| `frontend-service` | 8082 | Interface utilisateur (Thymeleaf), login maison | — |
| `notes-service` | 8083 | Notes médecin (non structurées) | MongoDB |
| `assessment-service` | 8084 | Calcul du risque diabète (interroge patient + notes en direct) | — |

**Seul le port 8082 est publié sur l'hôte.** La gateway est le point d'entrée unique du système ; elle-même, les 3 services back et les bases de données ne sont joignables que sur le réseau Docker interne (`medilabo-net`), jamais depuis l'extérieur — le réseau matérialise cette contrainte, pas seulement la convention applicative.