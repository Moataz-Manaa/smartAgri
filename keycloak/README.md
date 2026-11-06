# Configuration Keycloak de SmartAgri

Le fichier `smartagri-realm.json` permet de recréer la configuration du realm.
Il est destiné à une installation neuve, et ne constitue pas une sauvegarde des comptes.

## Contenu

- Realm `smartagri` et rôles `ADMIN`, `AGRICULTEUR`, `AGRONOME`.
- Client public `smartagri-angular`, redirection `http://localhost:4200/*`, origine `http://localhost:4200`, PKCE S256 et audience `smartagri-api`.
- Client confidentiel `smartagri-admin` avec authentification par compte de service.
- Compte technique avec `manage-users` et `view-realm` du client `realm-management`.
- Paramètres, scopes et parcours d'authentification conservés depuis l'export local.

Les comptes personnels et les anciennes clés de signature sont exclus.
Keycloak génère de nouvelles clés sur la nouvelle instance.
Le secret du client est remplacé par la référence `${KEYCLOAK_ADMIN_CLIENT_SECRET}`.
Cette variable doit être fournie **au conteneur Keycloak lors de l'import**, puis avec la même valeur au backend.

L'import ne crée pas de compte humain avec le rôle métier ADMIN.
Sur une installation neuve, créer ce premier compte depuis la console Keycloak et lui attribuer le rôle ADMIN dans le realm smartagri.
Les profils PostgreSQL ne sont pas exportés par cet outil : une base métier existante peut référencer des identifiants d'utilisateurs absents de la nouvelle instance.

## Vérifier le réimport sans toucher à l'installation actuelle

Prérequis : Docker Desktop démarré, Python disponible et image `quay.io/keycloak/keycloak:26.8.1` présente.
Depuis la racine du projet :

```powershell
python keycloak/verify_import.py
```

Ce script crée un conteneur neuf avec un nom et un port local disponibles.
Il génère des identifiants aléatoires, importe le realm et vérifie les réglages.
Il supprime ensuite son propre conteneur et son stockage de test.
Il ne monte aucun volume de l'instance actuelle et ne lit pas ses identifiants.
Les identifiants temporaires ne sont ni affichés ni enregistrés.

Vérification du 26 octobre 2026 : import réussi avec Keycloak 26.8.1.
Le test a confirmé les trois rôles métier, le client Angular, PKCE S256, l'audience smartagri-api,
la résolution du secret par variable et les permissions du compte technique.
Il a également créé, modifié et supprimé un utilisateur de test avec ce compte technique.
Le conteneur temporaire a été supprimé après ces vérifications.

## Installation sur une nouvelle machine

Les commandes suivantes concernent uniquement une installation neuve :
le nom du conteneur doit être disponible et le volume de données doit être vide.
Un realm déjà présent n'est pas remplacé par `--import-realm`.

Depuis la racine du projet, remplacer les valeurs d'exemple dans le terminal :

```powershell
$env:KEYCLOAK_ADMIN_CLIENT_SECRET="choisir_un_secret_de_client"
$env:KC_BOOTSTRAP_ADMIN_USERNAME="admin"
$env:KC_BOOTSTRAP_ADMIN_PASSWORD="choisir_un_mot_de_passe_administrateur"
$dossierImport = (Resolve-Path .\keycloak).Path

docker run -d --name smartagri-keycloak -p 127.0.0.1:8082:8080 --env KEYCLOAK_ADMIN_CLIENT_SECRET --env KC_BOOTSTRAP_ADMIN_USERNAME --env KC_BOOTSTRAP_ADMIN_PASSWORD --mount "type=volume,source=smartagri-keycloak-data,target=/opt/keycloak/data" --mount "type=bind,source=$dossierImport,target=/opt/keycloak/data/import,readonly" quay.io/keycloak/keycloak:26.8.1 start-dev --import-realm
```

Ouvrir `http://127.0.0.1:8082`. Le mode `start-dev` est utilisé pour le développement local.
Les identifiants bootstrap servent à la première administration Keycloak.
Le secret du client technique est distinct du mot de passe administrateur.

## Actualiser le fichier à partir d'un nouvel export

Effectuer l'export CLI lorsque l'instance source est arrêtée.
Conserver le fichier brut dans un dossier temporaire extérieur au projet.
Après l'export, redémarrer l'instance source.

```powershell
python keycloak/prepare_export.py "$env:TEMP\smartagri-keycloak-export\smartagri-realm.json"
python keycloak/verify_import.py
```

`prepare_export.py` retire les secrets, les composants portant les anciennes clés et les utilisateurs.
Il rétablit ensuite le compte technique et ses deux permissions prévues pour SmartAgri.
Il désactive les parcours interactifs du client technique et retire ses redirections de navigateur.
Une nouvelle fédération d'utilisateurs ou un fournisseur d'identité externe nécessite une vérification spécifique ; le script refuse ces cas.

Documentation officielle : [import/export Keycloak](https://www.keycloak.org/server/importExport).

## Recette des comptes avec le vrai backend

Depuis la racine du projet :

```powershell
python keycloak/verify_backend.py
```

Prérequis : Python, Maven, Java 21, Docker Desktop et les images
`quay.io/keycloak/keycloak:26.8.1` et `postgres:18-alpine`.
Les dépendances Maven doivent déjà être disponibles localement ; le script utilise le mode hors ligne.
Le cache Maven du profil courant est utilisé s'il existe.
Pour choisir un autre cache, définir `SMARTAGRI_MAVEN_REPOSITORY` dans le terminal.

Le script crée une instance Keycloak et une base PostgreSQL indépendantes, avec des noms uniques,
des ports locaux attribués automatiquement et des secrets aléatoires.
Il importe le realm nettoyé, puis lance `RecetteKeycloakTests`.
Le backend utilise ses vrais contrôleurs, son filtre JWT, ses services et ses repositories ;
Keycloak et PostgreSQL ne sont pas simulés.
Les appels à l'API du backend passent par MockMvc : cette recette ne remplace pas une vérification dans le navigateur.

Les tests couvrent :

1. La création, la modification du rôle et la désactivation d'un compte.
2. Un identifiant déjà utilisé dans Keycloak, avec un email différent.
3. Un refus SQL pendant la création : aucun profil conservé et compte Keycloak supprimé.
4. Un refus SQL pendant la modification : anciennes informations et ancien rôle restaurés.
5. Une panne pendant l'attribution initiale du rôle : création du compte annulée.
6. Une panne pendant le changement de rôle : compte et rôle précédents restaurés.
7. Une activation non confirmée : profil conservé, compte désactivé, puis reprise par activation.
8. Un échec de l'annulation : erreur explicite et compte désactivé à vérifier.
9. Un mot de passe refusé par une politique Keycloak plus stricte que la validation du formulaire.
10. Le parcours complet d'un administrateur, de deux agriculteurs et de deux agronomes :
    créations par l'API, affectations multiples, restrictions, doublons et retrait individuel.
11. Le refus d'un JWT déjà émis pendant la désactivation du compte, puis la reconnexion après réactivation.
12. Le refus des fonctions administratives avec un ancien JWT après retrait du rôle ADMIN.
13. Le refus d'un ancien JWT lorsque les rôles actuels sont ambigus ou absents.
14. Une panne du contrôle du compte, le retour au fonctionnement normal,
    le prévol CORS Angular et le refus des requêtes sans authentification.
15. Le catalogue du sprint 2 : cultures et variétés, consultation par les trois rôles,
    modification réservée à l'administrateur, doublons, contraintes PostgreSQL et rattachement des variétés.
16. Les campagnes du sprint 2 : dates ISO dans PostgreSQL, consultation par les trois rôles,
    transitions réservées à l'administrateur, clôture en lecture seule et protection contre les modifications concurrentes,
    rattachement multiple aux parcelles, filtrage des identifiants et des noms, totaux sans doublons
    et perte d'accès après retrait de la dernière affectation concernée.
17. Les plantations du sprint 2 : création et modification par un agriculteur affecté,
    culture déduite de la variété, consultation par l'agronome, pagination PostgreSQL,
    refus de saisie par l'agronome, clôture en lecture seule et retrait immédiat d'accès.

Les refus SQL sont déclenchés par une contrainte ajoutée uniquement dans la base jetable.
Un relais HTTP local provoque certaines réponses d'erreur ciblées avant de transmettre les autres appels au vrai Keycloak.
Il ne journalise ni les corps de requête ni les jetons.
Un client autorisant l'authentification directe est créé uniquement dans cette instance de test
pour obtenir de vrais JWT des comptes de recette ; la configuration Angular du projet reste inchangée.
Les tests optionnels sont désactivés lors d'un `mvn test` habituel.

Les deux conteneurs et leur stockage sont supprimés en fin de recette, y compris en cas d'échec.
Le rapport Maven est conservé dans `backend/target/recette-reelle.log`.

Résultat du 26 octobre 2026 : **17 tests réussis**, sans échec ni test ignoré.
Les contrôles dans le navigateur font l'objet d'une validation globale déclarée par l'utilisateur,
consignée séparément des tests automatisés dans [le guide final](../RECETTE_SPRINT_1.md).
