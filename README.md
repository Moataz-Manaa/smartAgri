# SmartAgri

Le sprint 1 est clôturé. Le **sprint 2 est en cours** : voir le [plan de travail et le suivi](SPRINT_2.md).

Plateforme intelligente de gestion agricole destinée à une seule société, possédant plusieurs exploitations et parcelles.

Ce README constitue le plan de travail du sprint 1. Les étapes seront réalisées dans l'ordre et leur avancement sera actualisé après chaque livraison vérifiée.

## Stack retenue

| Composant | Technologie |
| --- | --- |
| Backend métier | Spring Boot, Java, Maven |
| Frontend | Angular, TypeScript |
| Interface | Tailwind CSS |
| Identités et authentification | Keycloak |
| Base de données métier | PostgreSQL |

Keycloak gère les comptes, les mots de passe, les sessions et les rôles. SmartAgri conserve les profils métier et les affectations aux parcelles. L'authentification ne sera pas développée avec Spring Security, conformément au choix du projet. Le backend devra néanmoins vérifier les tokens Keycloak et appliquer les autorisations métier à chaque requête protégée.

## Conventions de développement

Ces conventions s'appliquent à toutes les étapes du projet.

- Utiliser les bases de Spring Boot et Angular avec un code lisible et des noms explicites.
- Privilégier les classes simples, les méthodes courtes, les conditions et les boucles faciles à comprendre.
- Éviter les abstractions génériques, les syntaxes complexes et les bibliothèques supplémentaires sans besoin concret.
- Expliquer brièvement les choix techniques lors de chaque étape. Commenter les parties qui nécessitent une explication sans commenter chaque ligne.
- Conserver les validations et les contrôles d'accès nécessaires, même en privilégiant une implémentation simple.

### Architecture Spring Boot

Organiser les packages par couche. Dans `dto`, regrouper les classes par domaine : `utilisateur`, `exploitation`, `parcelle` et `affectation`.

```text
com.example.backend/
├── config/
├── controller/
├── dto/
│   ├── utilisateur/
│   ├── exploitation/
│   ├── parcelle/
│   └── affectation/
├── entity/
├── enums/
├── exception/
├── repository/
└── service/
    ├── UtilisateurService.java
    ├── ExploitationService.java
    ├── ParcelleService.java
    ├── AffectationService.java
    └── impl/
        ├── UtilisateurServiceImpl.java
        ├── ExploitationServiceImpl.java
        ├── ParcelleServiceImpl.java
        └── AffectationServiceImpl.java
```

Chaque service métier possède une interface dans `service` et une classe d'implémentation dans `service/impl`. Par exemple, `ExploitationServiceImpl` implémente `ExploitationService`. Les contrôleurs reçoivent les requêtes et appellent les services ; les services portent les règles métier et utilisent les repositories pour accéder aux données. Les conversions entre entités et DTO restent explicites et simples.

### Architecture Angular

```text
src/app/
├── core/
│   ├── interceptors/
│   ├── guards/
│   ├── models/
│   └── services/
├── features/
│   ├── utilisateur/
│   ├── exploitation/
│   ├── parcelle/
│   └── affectation/
├── layout/
│   ├── header/
│   ├── footer/
│   └── sidebar/
├── shared/
│   ├── components/
│   ├── pipes/
│   ├── ui/
│   └── utils/
├── app.ts
├── app.html
├── app.css
├── app.config.ts
└── app.routes.ts
```

- `core/interceptors` : ajout du token aux appels API et traitement des erreurs communes.
- `core/guards` : contrôle de la navigation selon la connexion et les rôles.
- `core/models` : modèles TypeScript utilisés pour échanger avec le backend.
- `core/services` : services d'authentification et d'accès aux API métier.
- `features` : pages et formulaires regroupés par domaine métier, avec leurs composants spécifiques.
- `layout` : structure générale des pages, avec header, footer et sidebar si nécessaire.
- `shared/components` : composants réutilisables entre plusieurs fonctionnalités.
- `shared/ui` : éléments visuels communs, par exemple bouton, indicateur de chargement ou message d'erreur.
- `shared/pipes` : transformations d'affichage réutilisables.
- `shared/utils` : petites fonctions utilitaires partagées.

Les dossiers et composants seront ajoutés au fur et à mesure des besoins. Un composant propre à une fonctionnalité reste dans `features` ; il rejoint `shared` lorsqu'il est réellement réutilisé.

### Interface utilisateur

Créer une interface moderne avec Tailwind CSS : navigation claire, espaces réguliers, typographie lisible et couleurs cohérentes. Les formulaires affichent des libellés explicites et des erreurs près des champs. Les listes proposent des actions faciles à repérer, ainsi que des états de chargement et des messages lorsqu'elles sont vides. Prévoir une utilisation sur mobile et ordinateur, avec des contrastes lisibles et un focus clavier visible. Garder les écrans simples et les animations discrètes.

## Organisation actuelle

```text
smartAgri/
├── README.md
├── backend/       # Spring Boot, Maven, tests Java
└── frontend/      # Angular, Tailwind, tests Vitest
```

Versions déclarées : Java 21, Spring Boot 4.1.1, Angular 22 et Tailwind CSS 4.
Utiliser Lombok dans les entités et DTO. Aucun outil de migration : Hibernate met à jour le schéma local avec `spring.jpa.hibernate.ddl-auto=update`.
Les séquences des codes `EXP-01` et `PAR-01` sont initialisées au démarrage par Java dans `DatabaseInitializer`, indépendamment des identifiants des entités.

## Sprint 1 — Administration et contrôle des accès

### Objectif et règles

Une seule société possède plusieurs exploitations. Chaque parcelle appartient à une seule exploitation.
Seul l'administrateur crée ou modifie les utilisateurs, les exploitations, les parcelles et les affectations.
Plusieurs agriculteurs et agronomes peuvent être affectés à une même parcelle, et une personne à plusieurs parcelles.
Une affectation relie un profil utilisateur à une parcelle. Le couple utilisateur/parcelle est unique en base.

Les agriculteurs et agronomes consultent uniquement leurs parcelles et les exploitations correspondantes.
L'accès à une exploitation ne donne pas accès à toutes ses parcelles.
Retirer une affectation retire immédiatement l'accès correspondant sans modifier les autres affectations.

### Plan de travail et état au 26 octobre 2026

Les cases décrivent le code présent. Les résultats de tests et les limites de validation figurent plus bas.

#### Étape 1 — Fondations

- [x] Structurer Spring Boot et Angular selon les conventions du projet.
- [x] Configurer Tailwind et Lombok.
- [x] Configurer PostgreSQL et les secrets par variables d'environnement.
- [x] Utiliser Hibernate pour le schéma local, sans migrations.
- [x] Préparer les DTO par domaine, les services avec interfaces et implémentations et les erreurs API.
- [x] Compiler le backend et le frontend.

#### Étape 2 — Authentification Keycloak

- [x] Intégrer le realm local `smartagri` et les rôles ADMIN, AGRICULTEUR, AGRONOME.
- [x] Connecter Angular avec Authorization Code et PKCE S256.
- [x] Gérer connexion, déconnexion et renouvellement du token.
- [x] Envoyer le token uniquement vers l'API SmartAgri.
- [x] Vérifier la signature RS256, l'émetteur, l'audience et l'expiration dans le backend.
- [x] Vérifier l'état et le rôle actuels du compte à chaque requête API.
- [x] Fournir un export reproductible du realm sans secrets et vérifier son import dans une instance neuve.

#### Étape 3 — Utilisateurs

- [x] Enregistrer les profils métier avec un identifiant Keycloak unique, sans mot de passe local.
- [x] Créer, modifier, consulter, activer et désactiver les comptes.
- [x] Attribuer le rôle métier via le backend et l'API d'administration Keycloak.
- [x] Prévoir les compensations en cas d'erreur entre Keycloak et la base métier.
- [x] Réaliser les écrans Angular de liste et de formulaire.
- [x] Vérifier les erreurs de création/modification et les compensations avec Keycloak et PostgreSQL réels dans des conteneurs jetables.

#### Étape 4 — Exploitations et parcelles

- [x] Créer les entités et le rattachement de chaque parcelle à une exploitation.
- [x] Générer les codes avec une numérotation indépendante des identifiants.
- [x] Implémenter les créations, modifications, listes et détails.
- [x] Valider les surfaces positives, les coordonnées et l'exploitation de rattachement.
- [x] Réaliser les listes, formulaires et pages de détail Angular.

#### Étape 5 — Affectations et accès métier

- [x] Créer l'entité Affectation et la contrainte d'unicité utilisateur/parcelle.
- [x] Autoriser uniquement les agriculteurs et agronomes actifs lors d'une nouvelle affectation.
- [x] Ajouter et retirer individuellement les affectations depuis l'écran administrateur.
- [x] Refuser les doublons avec une réponse HTTP 409.
- [x] Filtrer les listes et détails côté backend selon l'identité connectée.
- [x] Filtrer aussi les parcelles d'une exploitation accessible.
- [x] Ajouter « Mes parcelles » et « Exploitations accessibles » pour les rôles métier.
- [x] Réserver les actions de gestion à l'administrateur dans les services et les gardes Angular.
- [x] Tester le retrait individuel et la perte d'accès sans impact sur les autres utilisateurs.

#### Étape 6 — Finalisation et recette

- [x] Adapter la navigation latérale aux rôles.
- [x] Afficher les états de chargement, listes vides et erreurs.
- [x] Tester les autorisations backend et les actions principales de l'écran d'affectation.
- [x] Tester les JWT expirés, mauvaise audience, mauvais émetteur et signature invalide.
- [x] Documenter les commandes de lancement et les vérifications.
- [x] Vérifier visuellement les nouveaux écrans sur ordinateur et mobile — validation globale déclarée par l'utilisateur le 26 octobre 2026.
- [x] Réaliser la recette complète avec PostgreSQL et Keycloak réels pour les trois rôles — API testée automatiquement et parcours navigateur validés globalement par l'utilisateur.
- [x] Ajouter la pagination des listes d'utilisateurs, exploitations, parcelles et affectations.
- [x] Réaliser une page d'accueil avec une vue d'ensemble et des liens propres à chaque rôle.

### Écran d'affectation

Se connecter comme administrateur, puis ouvrir **Affectations** dans la barre latérale.

1. Choisir une parcelle.
2. Choisir un agriculteur ou agronome actif.
3. Cliquer sur **Affecter à la parcelle**.
4. Répéter pour les autres personnes : la parcelle reste sélectionnée.
5. Utiliser **Retirer** sur une ligne pour supprimer uniquement cette affectation.

Le filtre par parcelle concerne l'affichage de la liste. Il est indépendant de la parcelle choisie dans le formulaire.

### API et autorisations

| API | Administrateur | Agriculteur / agronome |
| --- | --- | --- |
| GET /api/exploitations | Toutes | Exploitations avec au moins une affectation |
| GET /api/exploitations/{id} | Oui | Si au moins une parcelle affectée |
| GET /api/parcelles | Toutes | Parcelles affectées |
| GET /api/parcelles/{id} | Oui | Si affecté à cette parcelle |
| GET /api/parcelles/exploitation/{id} | Toutes celles de l'exploitation | Seulement celles affectées, sinon 403 |
| POST / PUT exploitations et parcelles | Oui | Non, 403 |
| Gestion / consultation des utilisateurs | Oui | Non, 403 |
| GET / POST / DELETE affectations | Oui | Non, 403 |

`GET /api/affectations?parcelleId=...` filtre la liste pour l'administrateur.
`POST /api/affectations` reçoit `{ "utilisateurId": 1, "parcelleId": 1 }`.
`DELETE /api/affectations/{id}` retire une seule affectation et renvoie 204.

### Pagination des listes

Les quatre listes principales utilisent une pagination effectuée en base de données,
avec un tri par identifiant décroissant.
La barre de pagination est un composant réutilisable dans `frontend/src/app/shared/components/pagination`.
Elle propose 10, 20 ou 50 lignes, le nombre de résultats et les boutons Précédent / Suivant.

| Endpoint | Paramètres |
| --- | --- |
| GET /api/utilisateurs/pagination | page, taille |
| GET /api/exploitations/pagination | page, taille |
| GET /api/parcelles/pagination | page, taille |
| GET /api/affectations/pagination | page, taille, parcelleId facultatif |

Dans l'API, la première page est `0` et la taille par défaut est `10`.
La taille doit être comprise entre 1 et 100 ; une page négative ou une taille invalide renvoie 400.
Exemple : `GET /api/parcelles/pagination?page=0&taille=10`.

La réponse contient `contenu`, `page`, `taille`, `totalElements` et `totalPages`.
Les totaux sont calculés uniquement sur les données autorisées pour l'utilisateur.
Le filtre des affectations est appliqué avant la pagination.
Changer la taille ou le filtre revient à la première page.
Si une suppression vide la dernière page, l'interface recharge la dernière page disponible.

Les endpoints de listes complètes restent disponibles pour les sélecteurs des formulaires
et pour la consultation des parcelles d'une exploitation.
Ces sélecteurs ne sont pas paginés dans cette étape.

### Accueil adapté au rôle

Après connexion, la racine de l'application redirige vers `/accueil`.
Le lien Accueil de la barre latérale et le logo SmartAgri permettent d'y revenir.
La route utilise la garde de lecture : une personne non connectée ou sans rôle métier
est redirigée vers la page d'accès refusé.

- Administrateur : totaux des exploitations, parcelles, profils utilisateurs enregistrés et affectations,
  avec des raccourcis pour créer une exploitation, une parcelle, un utilisateur ou gérer les affectations.
- Agriculteur : totaux des parcelles affectées et des exploitations accessibles, avec des liens de consultation.
- Ingénieur agronome : présentation dédiée à son accompagnement, avec les mêmes indicateurs limités à ses affectations et les liens de consultation autorisés.

Les indicateurs proviennent des totaux des API paginées, avec une seule ligne demandée par appel.
Les deux rôles métier n'appellent pas les API de gestion des utilisateurs ou des affectations.
Le nombre d'utilisateurs correspond aux profils métier enregistrés dans SmartAgri, et non à tous les comptes techniques ou administratifs de Keycloak.
Un message explique l'absence d'affectation ; en cas d'erreur, les indicateurs restent indisponibles au lieu d'afficher un zéro incorrect.
Le bouton Actualiser permet de réessayer et reste désactivé pendant le chargement.

Cette page couvre l'accueil du sprint 1. Les indicateurs de cultures, météo, alertes et prédictions
restent dans les sprints suivants.

Le backend valide d'abord le JWT puis consulte le compte et son rôle actuel dans Keycloak.
Un compte désactivé est refusé avec 401 ; un rôle absent ou des rôles métier ambigus avec 403.
Un compte supprimé est refusé avec 401. Une indisponibilité de Keycloak bloque les appels avec 503.
Cette vérification permet de retirer les droits même si un ancien JWT n'est pas encore expiré.
Les affectations sont relues en base à chaque requête.

Le menu Angular utilise les rôles du token : après un changement de rôle, une reconnexion actualise son affichage.
Les contrôles backend utilisent immédiatement le rôle actuel, indépendamment du menu affiché.

## Lancement local

### 1. Keycloak et PostgreSQL

Démarrer Docker Desktop puis le conteneur existant :

```powershell
docker start smartagri-keycloak
docker logs --tail 30 smartagri-keycloak
```

Keycloak doit répondre à `http://127.0.0.1:8082` (port hôte 8082 vers port conteneur 8080).
Le conteneur doit déjà contenir le realm `smartagri`, les trois rôles et les clients configurés.
Le client public `smartagri-angular` utilise les redirections Angular sur `http://localhost:4200`, PKCE S256 et l'audience `smartagri-api`.
Le client confidentiel `smartagri-admin` utilise un compte de service avec les rôles nécessaires, notamment `manage-users` et `view-realm` du client `realm-management`.

Démarrer également PostgreSQL. La configuration actuelle utilise la base `smartagri` et l'utilisateur `smartagri` sur le port 5432.
Le mot de passe demandé ci-dessous est celui de cet utilisateur PostgreSQL.

### 2. Backend

Dans un terminal PowerShell depuis la racine du projet :

```powershell
cd backend
$env:DB_PASSWORD="mot_de_passe_de_l_utilisateur_smartagri"
$env:KEYCLOAK_ADMIN_CLIENT_SECRET="secret_du_client_smartagri-admin"
mvn spring-boot:run
```

Ces valeurs sont des exemples à remplacer dans le terminal. Ne pas enregistrer les vrais secrets dans les fichiers.
L'API écoute sur `http://localhost:8081`.
Au redémarrage, Hibernate ajoute la table `affectations` si elle est absente.

### 3. Frontend

Dans un autre terminal depuis la racine :

```powershell
cd frontend
ng serve
```

Ouvrir `http://localhost:4200` et se connecter.

## Vérifications du 26 octobre 2026

| Vérification | Résultat |
| --- | --- |
| Compilation et tests backend | 55 tests réussis, incluant le catalogue, les campagnes et les plantations du sprint 2 |
| Recette API avec Keycloak et PostgreSQL réels | 17 tests réussis dans deux conteneurs temporaires : comptes, trois rôles, affectations, sessions, catalogue, campagnes et plantations |
| Tests Angular | 64 tests réussis, incluant le catalogue, les campagnes et les plantations du sprint 2 |
| Compilation Angular de production | Réussie |
| Réimport du realm Keycloak dans un conteneur neuf | Réussi : rôles, Angular, PKCE, audience, secret par variable et permissions du compte technique vérifiés |

Les tests backend exécutent les contrôleurs, services et repositories avec une base H2 temporaire.
Le fournisseur de comptes Keycloak est simulé pour les tests d'autorisation.
Les tests du validateur JWT utilisent de vraies signatures RSA et un serveur local de clés publiques.
Les tests Angular vérifient les menus selon le rôle, les gardes, les comptes affectables, le retrait individuel et le traitement d'un doublon.
Les tests de pagination couvrent les limites, le tri et les pages suivantes, les totaux filtrés par affectation,
l'absence de doublons dans les exploitations, le filtre des affectations, les changements de taille
et le retour à une page existante après suppression.
Les tests d'accueil vérifient les trois rôles, les raccourcis autorisés, l'absence d'appels administratifs
pour les agriculteurs et agronomes, les états vide/chargement/erreur, la nouvelle tentative,
la redirection depuis la racine et le refus d'accès sans connexion ou sans rôle métier.
La validation globale du fonctionnement et des écrans a été déclarée par l'utilisateur le 26 octobre 2026.
L'agent n'a pas observé les écrans dans Firefox : l'outil de contrôle a interrompu l'accès,
le filtrage des URL n'étant pas pris en charge pour ce navigateur Windows.
La validation manuelle déclarée est distinguée des résultats de tests automatisés dans le guide de recette.

Commandes usuelles :

```powershell
# Dans backend/
mvn test

# Dans frontend/
npm test -- --watch=false
npm run build
```

Dans l'environnement de l'agent, Maven utilise explicitement le cache existant :

```powershell
mvn -o "-Dmaven.repo.local=C:/Users/manaa/.m2/repository" -B test
```

Les tests ne modifient pas la base PostgreSQL ni les comptes Keycloak réels.
La configuration reproductible et ses commandes sont documentées dans [keycloak/README.md](keycloak/README.md).
Le fichier [keycloak/smartagri-realm.json](keycloak/smartagri-realm.json) exclut les comptes personnels et les anciennes clés privées.
Son import a été vérifié avec Keycloak 26.8.1 dans un conteneur temporaire indépendant.
Le compte technique importé a pu créer, modifier et supprimer un utilisateur de test ; le conteneur a ensuite été supprimé.
La recette optionnelle des comptes se lance avec `python keycloak/verify_backend.py`.
Elle vérifie aussi les compensations après refus SQL ou panne ciblée de l'API Keycloak,
avec de vrais JWT et deux services Docker indépendants des services habituels.
Les 16 cas et les commandes sont détaillés dans [keycloak/README.md](keycloak/README.md#recette-des-comptes-avec-le-vrai-backend).
Les parcours API des trois rôles sont validés : affectations multiples, filtrage des listes et des détails,
retrait individuel, désactivation et ancien JWT, changement de rôle et ancien JWT,
rôles ambigus ou absents, indisponibilité du contrôle de compte et prévol CORS.
L'utilisateur a confirmé globalement le bon fonctionnement après la demande de vérification des parcours et des écrans.
Le [guide de recette finale](RECETTE_SPRINT_1.md) consigne cette validation déclarée,
sans attribuer à l'agent des observations visuelles qu'il n'a pas réalisées.
Le sprint 1 est clôturé le 26 octobre 2026 : **42 tâches terminées sur 42**.

## Scénario de recette de référence

1. L'administrateur prépare deux exploitations et trois parcelles : PAR-01/PAR-02 dans EXP-01, PAR-03 dans EXP-02.
2. Affecter A à PAR-01/PAR-02, B à PAR-01/PAR-03 et un agronome aux trois parcelles.
3. Vérifier que A ne consulte pas PAR-03, même par URL ou appel API direct.
4. Vérifier que B consulte EXP-01 mais seulement PAR-01 dans son détail.
5. Retirer A de PAR-01 : B et l'agronome conservent leur accès et A conserve PAR-02.
6. Vérifier les refus des actions administratives pour l'agriculteur et l'agronome.
7. Désactiver un compte ou changer son rôle alors qu'une session existe, puis vérifier le refus à la prochaine requête API.
8. Vérifier les créations/modifications de comptes, les erreurs Keycloak et l'affichage sur mobile.

## Suite du projet

Cultures, variétés, plantations et suivi agricole : sprint 2.
Météo, alertes et tableaux de bord métier : sprint 3.
Machine Learning, validation des diagnostics, prédictions, conteneurisation applicative, CI/CD et déploiement : sprint 4.
La configuration locale de Keycloak et PostgreSQL reste nécessaire au sprint 1.
