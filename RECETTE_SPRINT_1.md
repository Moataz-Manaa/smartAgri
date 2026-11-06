# Recette finale du sprint 1

## État actuel

Les parcours API des trois rôles sont vérifiés avec PostgreSQL et Keycloak réels
dans deux conteneurs temporaires : **14 tests réussis**.
Le 26 octobre 2026, l'utilisateur a confirmé globalement le bon fonctionnement
après la demande de vérification des parcours Angular et de l'affichage.
Cette confirmation est enregistrée comme une validation manuelle déclarée ;
les tailles de fenêtre et les résultats individuels par écran n'ont pas été détaillés.
Le sprint 1 est clôturé sur cette base et sur les résultats des tests automatisés.
Les étapes ci-dessous restent disponibles pour reproduire la recette.

Le rapport technique se trouve dans `backend/target/recette-reelle.log`.
La recette API se relance depuis la racine avec :

```powershell
python keycloak/verify_backend.py
```

Ce script supprime ses conteneurs après les tests ; son jeu de données n'est donc pas disponible
dans l'application habituelle sur le port 4200.

## Préparer la vérification dans le navigateur

Démarrer les services habituels :

```powershell
docker start smartagri-keycloak
```

Dans un terminal du backend, définir les variables de connexion habituelles puis lancer :

```powershell
mvn spring-boot:run
```

Dans un autre terminal, depuis `frontend` :

```powershell
ng serve
```

Ouvrir `http://localhost:4200`.
Utiliser des comptes de recette pour les changements de rôle et les désactivations.
Les mots de passe sont choisis dans le formulaire ; aucun mot de passe n'est consigné dans ce guide.

## 1. Parcours administrateur

Se connecter comme administrateur.

- Ouvrir l'accueil : vérifier le nom du compte, le badge Administrateur et les quatre indicateurs.
- Vérifier les liens Accueil, Exploitations, Parcelles, Utilisateurs et Affectations.
- Vérifier que le logo SmartAgri revient à l'accueil.
- Créer deux exploitations de recette : Sfax et Nabeul.
- Créer deux parcelles dans Sfax et une dans Nabeul.
- Vérifier les codes générés automatiquement, par exemple EXP-01 et PAR-01,
  sans supposer que la numérotation commence à 1 dans une base déjà utilisée.
- Modifier le nom d'une parcelle : son code reste identique.
- Consulter les détails d'une exploitation et d'une parcelle.
- Soumettre un formulaire incomplet : les erreurs sont lisibles près des champs et la création est refusée.
- Créer les comptes de recette A et B avec le rôle Agriculteur, et un compte avec le rôle Ingénieur agronome.
- Affecter A aux deux parcelles de Sfax ; affecter B à la première parcelle de Sfax et à celle de Nabeul.
- Affecter l'agronome aux trois parcelles.
- Essayer d'ajouter une affectation identique : vérifier le message de doublon et l'absence de ligne supplémentaire.
- Filtrer les affectations par parcelle, puis revenir à toutes les parcelles.
- Vérifier le changement du nombre de lignes par page. Si une liste contient plus de 10 éléments,
  vérifier Précédent / Suivant et l'absence de lignes répétées entre deux pages.

## 2. Parcours agriculteur

Se déconnecter puis se connecter avec A.

- L'accueil affiche le badge Agriculteur et les indicateurs limités à ses affectations.
- Le menu affiche Mes parcelles et Exploitations accessibles.
- Les liens de gestion des utilisateurs et des affectations ne sont pas affichés.
- Les deux parcelles de Sfax sont consultables ; celle de Nabeul n'apparaît pas.
- Le détail de Sfax affiche uniquement les parcelles auxquelles A est affecté.
- Saisir directement l'URL de la parcelle de Nabeul, en utilisant son véritable identifiant :
  l'application affiche un refus, et l'API répond 403.
- Essayer `/utilisateurs`, `/affectations` et `/parcelles/nouvelle` :
  la garde redirige vers la page d'accès refusé.

Avec B :

- Les exploitations Sfax et Nabeul sont accessibles.
- Le détail de Sfax ne révèle pas la deuxième parcelle à laquelle B n'est pas affecté.

## 3. Parcours ingénieur agronome

Se connecter avec le compte agronome.

- L'accueil affiche le badge Ingénieur agronome et sa présentation dédiée.
- Les trois parcelles affectées sont consultables.
- Les informations des exploitations correspondantes sont accessibles.
- Les actions de création/modification administratives ne sont pas proposées.
- Un accès direct à `/utilisateurs` ou `/affectations` est refusé.

## 4. Affectations et sessions existantes

- Garder A connecté dans une fenêtre et utiliser l'administrateur dans une autre session.
- Retirer uniquement l'affectation de A à la première parcelle de Sfax.
- Actualiser cette parcelle dans la session de A : son accès est refusé.
- Vérifier que A conserve sa deuxième parcelle, tandis que B et l'agronome conservent leurs accès.
- Désactiver le compte de recette A : sa prochaine requête API est refusée avec 401.
- Réactiver A et se reconnecter : l'accès retrouve les limites prévues par les affectations restantes.
- Pour vérifier un changement de rôle, utiliser un compte administrateur de recette distinct du compte principal :
  le transformer en agriculteur pendant qu'une session reste ouverte.
  Les appels aux fonctions administratives doivent être refusés avec 403.
- Se reconnecter après le changement de rôle pour actualiser le menu :
  l'interface utilise les rôles du token, tandis que le backend vérifie immédiatement le rôle actuel.

## 5. Présentation sur ordinateur et mobile

Effectuer les vérifications suivantes sur l'accueil, les quatre listes,
les trois formulaires et les pages de détail :

| Taille de fenêtre | Résultat attendu |
| --- | --- |
| Ordinateur, environ 1440 px | Barre latérale visible, contenu lisible, cartes et formulaires bien alignés |
| Tablette, environ 768 px | Contenu réorganisé sans chevauchement ; navigation accessible avec Menu |
| Mobile, environ 375 px | Formulaires en une colonne, boutons accessibles et aucun élément coupé |

- Sur mobile, ouvrir Menu, choisir un lien, puis vérifier sa fermeture.
- Les grands tableaux peuvent défiler horizontalement dans leur propre zone.
- La page entière ne doit pas nécessiter de défilement horizontal.
- Vérifier la lisibilité des libellés, des messages d'erreur et des sélecteurs.
- Vérifier le parcours au clavier avec Tab et la visibilité du focus.
- Vérifier le bouton Actualiser et son état désactivé pendant le chargement.
- Avec un compte sans affectation, vérifier le message explicatif de l'accueil et les listes vides.

## Compte rendu de clôture — 26 octobre 2026

| Vérification | Résultat actuel |
| --- | --- |
| API : administration, agriculteurs, agronomes | Validée automatiquement |
| API : retrait individuel des affectations | Validé automatiquement |
| API : désactivation et ancien JWT | Validée automatiquement |
| API : changement/retrait/ambiguïté de rôle et ancien JWT | Validé automatiquement |
| API : erreurs et compensations Keycloak/PostgreSQL | Validées automatiquement |
| Navigateur : parcours administrateur | Inclus dans la validation globale déclarée par l'utilisateur |
| Navigateur : parcours agriculteur | Inclus dans la validation globale déclarée par l'utilisateur |
| Navigateur : parcours agronome | Inclus dans la validation globale déclarée par l'utilisateur |
| Présentation ordinateur / tablette / mobile | Validation globale déclarée ; tailles exactes non consignées |

L'agent n'a pas réalisé d'observation visuelle dans Firefox.
Après l'autorisation de l'utilisateur, l'outil a interrompu le contrôle
car le filtrage des URL n'était pas pris en charge pour ce navigateur Windows.
La clôture des contrôles manuels repose donc sur la confirmation de l'utilisateur,
et non sur une observation ou une capture effectuée par l'agent.
