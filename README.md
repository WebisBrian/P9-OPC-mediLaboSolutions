# MédiLabo Solutions

Application d'aide au dépistage du risque de diabète de type 2, développée pour la clinique Abernathy. Mono-repo de 5 microservices Spring Boot déployés ensemble.

Voir `CLAUDE.md` pour le cadrage complet (stack, conventions, sécurité, découpage par sprints) et `docs/features/` pour la documentation détaillée de chaque fonctionnalité.

## Clés RS256 (sécurité JWT)

La gateway authentifie les utilisateurs (HTTP Basic) puis émet un JWT signé en **RS256** pour chaque requête relayée vers les services back. Ce JWT est validé par chaque back avant de traiter la requête.

- **Pourquoi RS256 (asymétrique) plutôt qu'un secret partagé symétrique** : la gateway signe avec la clé **privée**, les services back valident avec la clé **publique** correspondante. Un service back ne détient jamais la clé privée : il ne peut donc jamais forger un token, seulement en vérifier l'authenticité. Cela réduit la surface d'attaque en cas de compromission d'un back.
- **Pourquoi les clés ne sont pas versionnées** : une clé privée ne se commit jamais dans un dépôt Git, même en contexte de démonstration. Chaque environnement (poste de développement, CI, prod) génère ou reçoit sa propre paire de clés.

### Procédure de montage (développement local)

```bash
./scripts/generate-keys.sh
```

Génère une paire de clés RSA 2048 bits :
- clé privée (PKCS#8 PEM) → `gateway-service/src/main/resources/keys/private_key.pem`
- clé publique (X.509/SPKI PEM), dupliquée dans chaque back → `<service>/src/main/resources/keys/public_key.pem`

Si des clés existent déjà, le script demande confirmation avant de les écraser (ou utiliser `--force` pour écraser sans confirmation).

Lancer ensuite les services normalement (`./run-dev.sh <service>`).

### Note production

En production, la clé privée serait fournie via un gestionnaire de secrets (ex. Vault, secret manager cloud), jamais présente sur le filesystem du dépôt ni de l'image Docker.