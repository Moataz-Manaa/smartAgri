# Sprint 2 — Cultures, plantations et suivi agricole

## Objectif

Permettre à un agriculteur de suivre les plantations de ses parcelles, depuis leur préparation
jusqu'aux récoltes, et à un agronome affecté de consulter leur historique.
Le sprint reprend les conventions de code du sprint 1.

## Conventions conservées

- Spring Boot avec Lombok, repositories et services sous forme d'interfaces et d'implémentations.
- DTO regroupés dans des dossiers par domaine.
- Conditions, boucles et conversions explicites, sans architecture générique supplémentaire.
- Angular avec `core/models`, `core/services`, `core/guards`, `features`, `layout` et `shared`.
- Formulaires avec FormsModule, états avec signal et styles Tailwind/CSS simples.
- Réutilisation du composant de pagination et des contrôles Keycloak existants.
- Schéma local géré par Hibernate, sans migrations.

## Décision confirmée pour le catalogue

L'utilisateur a choisi le 26 octobre 2026 : **l'administrateur uniquement crée et modifie les cultures et les variétés**.
L'agriculteur et l'agronome peuvent consulter ce catalogue commun.
Ce catalogue contient des données de référence ; les règles d'affectation aux parcelles
s'appliqueront aux plantations et aux opérations agricoles.

## Plan de travail

### Étape 1 — Préparer le sprint

- [x] Consigner le plan et conserver les conventions du projet.
- [x] Consulter le diagramme de référence pour les cultures et les variétés.
- [x] Confirmer les permissions du catalogue avec l'utilisateur.

### Étape 2 — Cultures et variétés

- [x] Créer les entités Culture et Variete, avec une culture obligatoire pour chaque variété.
- [x] Ajouter les DTO par domaine, les repositories, les interfaces de service et leurs implémentations.
- [x] Implémenter création, modification, consultation et listes paginées.
- [x] Valider les champs, les doublons et l'existence de la culture.
- [x] Créer les listes et formulaires Angular, avec filtre des variétés par culture.
- [x] Ajouter la navigation et appliquer les autorisations dans l'API et dans Angular.
- [x] Tester les règles et les parcours Angular, et compiler les applications.
- [ ] Valider visuellement les nouveaux écrans avec les trois rôles.

### Étape 3 — Campagnes agricoles

- [x] Préciser les champs, les droits et les règles de clôture d'une campagne.
- [x] Créer les entités, DTO, repositories, services et contrôleurs.
- [x] Réaliser la liste, le détail et le formulaire Angular.
- [x] Vérifier les dates et les statuts Planifiée, En cours et Clôturée.
- [ ] Valider visuellement les nouveaux écrans de campagne.

### Étape 4 — Plantations

- [x] Définir le rattachement à une parcelle, une variété et une campagne.
- [x] Implémenter la création, la modification et la consultation sur les parcelles autorisées.
- [x] Vérifier les dates, les superficies et la cohérence de la variété avec sa culture.
- [x] Afficher les plantations dans le détail d'une parcelle, avec filtres et pagination.
- [x] Protéger la cohérence du catalogue lorsqu'il est référencé par une plantation.
- [ ] Valider visuellement les nouveaux écrans des plantations sur ordinateur et mobile.

### Étape 5 — Observations agricoles

- [ ] Enregistrer les observations avec leur date, leur plantation et leur auteur.
- [ ] Réaliser les écrans d'ajout, de modification et de consultation.
- [ ] Vérifier les accès de l'agriculteur et de l'agronome selon leurs affectations.

### Étape 6 — Irrigation

- [ ] Enregistrer les opérations effectuées avec date, volume, unité et commentaire.
- [ ] Ajouter la consultation, la modification et le total par plantation.

### Étape 7 — Fertilisation et traitements

- [ ] Enregistrer les produits, dates, quantités, unités et informations propres à chaque opération.
- [ ] Réaliser les formulaires et l'historique par plantation.

### Étape 8 — Récoltes

- [ ] Enregistrer plusieurs récoltes par plantation avec date, quantité et unité.
- [ ] Afficher la quantité totale et le rendement réel lorsque les données le permettent.

### Étape 9 — Historique agricole

- [ ] Regrouper observations, irrigation, fertilisation, traitements et récoltes dans la plantation.
- [ ] Ajouter les filtres, la pagination et les états vides nécessaires.

### Étape 10 — Recette et livraison

- [ ] Vérifier les parcours complets et la perte d'accès après retrait d'une affectation.
- [ ] Exécuter les tests et les compilations du sprint.
- [ ] Valider les écrans sur ordinateur et mobile, puis consigner la recette.

## Première livraison — Catalogue agricole

### Données

| Entité | Champs métier |
| --- | --- |
| Culture | id, nom obligatoire (100 caractères), description facultative (255), type facultatif (100) |
| Variete | id, nom obligatoire (100 caractères), description facultative (255), culture obligatoire |

Le nom d'une culture est unique dans le catalogue.
Le nom d'une variété est unique au sein de sa culture ; le même nom reste possible dans une autre culture.
La comparaison ignore la casse et les espaces en début et fin de nom.
Une colonne interne `nomNormalise` garantit aussi l'unicité en base ; elle n'est pas exposée dans les DTO.

### API

| Opération | Cultures | Variétés |
| --- | --- | --- |
| Liste complète | GET /api/cultures | GET /api/varietes |
| Liste paginée | GET /api/cultures/pagination | GET /api/varietes/pagination |
| Consultation | GET /api/cultures/{id} | GET /api/varietes/{id} |
| Création | POST /api/cultures | POST /api/varietes |
| Modification | PUT /api/cultures/{id} | PUT /api/varietes/{id} |

Les listes de variétés acceptent `cultureId` comme filtre facultatif.
Les listes paginées utilisent `page` et `taille`, comme au sprint 1.
Un doublon renvoie 409, une culture absente 404, des données invalides 400,
une tentative de modification par un autre rôle 403 et une requête non authentifiée 401.

### Interface

- `/cultures` : catalogue et liens vers les variétés de chaque culture.
- `/cultures/nouvelle` et `/cultures/:id/modifier` : formulaires administrateur.
- `/varietes` : catalogue filtrable par culture.
- `/varietes/nouvelle` et `/varietes/:id/modifier` : formulaires administrateur.
- Depuis une culture, le filtre et la culture d'une nouvelle variété sont préselectionnés.
- Sans culture disponible, le formulaire de variété propose de créer une culture.

### Vérifications

Résultats du 26 octobre 2026 :

| Vérification | Résultat |
| --- | --- |
| Tests backend habituels | 32 réussis, dont 8 tests du catalogue |
| Recette avec PostgreSQL et Keycloak réels | 15 réussis, dont le catalogue avec les trois rôles |
| Tests Angular | 37 réussis, dont 10 tests du catalogue |
| Compilation backend | Réussie |
| Compilation Angular de production | Réussie |

Les tests couvrent les droits de lecture et de modification, les doublons normalisés,
les contraintes en base, les relations, les données invalides, la pagination,
les formulaires et le filtre de culture.
Ils vérifient aussi qu'une ancienne réponse ne remplace pas le résultat d'un nouveau filtre.
La recette réelle utilise des conteneurs temporaires supprimés après les tests.
La vérification visuelle reste à confirmer par l'utilisateur pour ces nouveaux écrans.

### Recette manuelle de cette livraison

1. Redémarrer le backend et ouvrir Angular sur `http://localhost:4200`.
2. Comme administrateur, créer une culture Olivier avec son type et une description.
3. Créer une variété Chemlali associée à Olivier.
4. Modifier les informations, filtrer les variétés par culture et consulter les listes.
5. Essayer de créer à nouveau ` OLIVIER ` : le doublon doit être refusé.
6. Comme agriculteur puis agronome, vérifier la consultation et l'absence des actions de modification.
7. Vérifier les formulaires et les listes sur mobile.

## Deuxième livraison — Campagnes agricoles

### Droits et données

Le 26 octobre 2026, l'utilisateur a confirmé que l'administrateur reste responsable des campagnes.
L'administrateur peut créer, modifier, démarrer et clôturer une campagne.
Une campagne est rattachée à une ou plusieurs parcelles choisies par l'administrateur.
Les agriculteurs et agronomes consultent uniquement les campagnes concernant au moins une de leurs parcelles affectées.
Dans une campagne partagée, les données des autres parcelles ne sont pas exposées.
Les mêmes restrictions s'appliqueront ensuite aux plantations et aux opérations.

La campagne contient :

- Un nom obligatoire de 100 caractères maximum, unique sans tenir compte de la casse
  et des espaces en début ou fin de nom.
- Une description facultative de 255 caractères maximum.
- Une date de début et une date de fin obligatoires, stockées avec LocalDate.
- Un statut géré séparément des informations générales.
- Une sélection obligatoire d'au moins une parcelle lors de la création ou de la modification.

La date de fin doit être strictement postérieure à la date de début.
L'API échange les dates au format `YYYY-MM-DD`, sans heure.
Des périodes historiques peuvent être enregistrées.
Le nom normalisé et la version technique restent internes au backend.
Une parcelle peut participer à plusieurs campagnes.
La relation est conservée dans la table de liaison `campagne_parcelles`, créée par Hibernate sans migration.

### Accès selon les affectations

L'administrateur voit toutes les campagnes et toutes leurs parcelles.
Pour les deux autres rôles, le filtrage est appliqué dans les requêtes de liste avant la pagination et le comptage.
Le détail vérifie aussi l'accès, y compris lorsqu'un identifiant est saisi directement.
Les champs `parcelleIds` et `parcelles` des réponses contiennent uniquement les parcelles accessibles à la personne.
Une campagne n'apparaît qu'une fois même lorsque plusieurs de ses parcelles lui sont affectées.

Retirer la dernière affectation concernée supprime l'accès à cette campagne à la requête suivante,
sans modifier les droits des autres utilisateurs.
Retirer une parcelle d'une campagne ouverte peut également supprimer l'accès à cette campagne,
sans supprimer l'affectation générale de l'utilisateur à sa parcelle.

### Campagnes déjà enregistrées sans parcelle

Ces campagnes restent visibles uniquement pour l'administrateur jusqu'au rattachement de leurs parcelles.
Aucune parcelle n'est associée automatiquement.
Pour une campagne ouverte, l'administrateur complète le formulaire de modification.
Pour une ancienne campagne clôturée sans parcelle, le détail propose **Associer les parcelles** :
le formulaire autorise uniquement ce rattachement initial, sans modifier les dates, le nom ou le statut.
Une fois les parcelles définies, la campagne clôturée redevient entièrement en lecture seule.

### Cycle de vie

Une campagne est toujours créée avec le statut **Planifiée**.
Le cycle est manuel : **Planifiée → En cours → Clôturée**.
Les dates ne changent pas automatiquement le statut.

Le détail propose à l'administrateur de démarrer une campagne planifiée,
puis de clôturer une campagne en cours. La clôture demande une confirmation dans l'interface.
Une campagne clôturée reste consultable et ses informations ne peuvent plus être modifiées.
Le rattachement initial d'une ancienne campagne sans parcelle est l'exception administrative décrite ci-dessus.
Une campagne ouverte doit avoir ses parcelles définies avant un changement de statut.
Le retour à un ancien statut ou le saut direct de Planifiée à Clôturée est refusé avec 409.
Répéter une demande du statut déjà enregistré renvoie l'état actuel sans changement supplémentaire.

Une version interne JPA empêche des modifications simultanées de s'écraser,
notamment lorsqu'une clôture et une modification arrivent en même temps.
En cas de conflit, l'API renvoie 409 et demande d'actualiser la campagne.

### API et écrans

| Opération | API |
| --- | --- |
| Liste complète | GET /api/campagnes |
| Liste paginée | GET /api/campagnes/pagination |
| Consultation | GET /api/campagnes/{id} |
| Création | POST /api/campagnes |
| Modification des informations | PUT /api/campagnes/{id} |
| Changement de statut | PUT /api/campagnes/{id}/statut |
| Rattachement initial d'une campagne sans parcelle | PUT /api/campagnes/{id}/parcelles |

Les listes acceptent le filtre facultatif `statut` avec les valeurs
`PLANIFIEE`, `EN_COURS` et `CLOTUREE`.
La pagination utilise les mêmes paramètres `page` et `taille` que les autres modules.
Le tri présente les dates de début les plus récentes en premier, puis les identifiants décroissants.

Exemple de changement de statut :

```json
{ "statut": "EN_COURS" }
```

La création et la modification incluent une liste `parcelleIds`, par exemple `[1, 2]`.
Le rattachement initial reçoit uniquement `{ "parcelleIds": [1, 2] }`.
Une liste vide, un identifiant invalide ou répété est refusé avec 400 ; une parcelle absente avec 404.

Les routes Angular sont `/campagnes`, `/campagnes/nouvelle`,
`/campagnes/:id` et `/campagnes/:id/modifier`.
Les écrans sont chargés à leur ouverture via des fonctions async simples dans les routes.
Ils réutilisent la pagination et un pipe partagé pour les libellés des statuts.
Un lien Campagnes est disponible dans l'accueil et dans la navigation latérale.

### Vérifications

| Vérification | Résultat au 26 octobre 2026 |
| --- | --- |
| Tests backend habituels | 47 réussis, dont 15 tests des campagnes |
| Recette PostgreSQL et Keycloak réels | 16 réussis, incluant les campagnes et les trois rôles |
| Tests Angular | 54 réussis, dont 17 tests des campagnes |
| Compilations backend et Angular | Réussies |
| Budget du chargement initial Angular | Respecté |

Les tests couvrent les dates ISO, les dates manquantes ou invalides, les doublons,
les transitions interdites, la clôture en lecture seule, les droits des trois rôles,
la pagination filtrée et la protection contre une modification concurrente après clôture.
Les tests Angular vérifient aussi la confirmation de clôture et le blocage des doubles demandes.
Les tests de rattachement vérifient la sélection multiple obligatoire, le filtrage des identifiants et des noms,
les totaux sans doublons, la perte immédiate d'accès et le rattachement initial des anciennes campagnes.
La présentation visuelle des nouveaux écrans reste à confirmer par l'utilisateur.

### Recette manuelle

1. Redémarrer le backend et ouvrir **Campagnes** dans le menu Angular.
2. Comme administrateur, créer « Campagne maraîchère 2027 » du 1er janvier au 31 décembre 2027 et choisir une ou plusieurs parcelles.
3. Vérifier le statut Planifiée, les informations et la modification du formulaire.
4. Depuis le détail, démarrer la campagne et vérifier le statut En cours.
5. Clôturer la campagne, confirmer, puis vérifier la consultation sans modification possible.
6. Comme agriculteur puis agronome, vérifier que seules les campagnes de leurs parcelles sont visibles, avec uniquement les parcelles autorisées dans le détail.
7. Vérifier les dates invalides, le filtre par statut et l'affichage sur mobile.

## Troisième livraison — Plantations par campagne

### Modèle confirmé

L'utilisateur a choisi une **fiche de suivi par campagne**, y compris pour les cultures pérennes.
Chaque fiche référence une parcelle, une campagne et une variété. La culture est déduite de la variété :
elle n'est pas enregistrée une seconde fois dans la plantation.
Pour suivre les mêmes oliviers l'année suivante, l'agriculteur crée une fiche pour la nouvelle campagne.

Champs : date de plantation obligatoire, superficie positive en hectares, nombre entier de plants
facultatif et positif, date de récolte prévue facultative, commentaire de 255 caractères maximum
et statut `PLANIFIEE`, `EN_COURS` ou `TERMINEE`.
Le statut de cette fiche est indépendant du statut de la campagne et reste modifiable tant que la campagne est ouverte.
La fiche ne possède pas de code métier supplémentaire.

La date de plantation peut précéder le début de la campagne pour représenter des arbres existants,
mais ne peut pas dépasser sa fin. La récolte prévue doit appartenir à la campagne et être à la date
de plantation ou après. La superficie de chaque fiche ne peut pas dépasser celle de sa parcelle.
Les superficies des différentes fiches ne sont pas additionnées automatiquement, pour permettre
plusieurs cultures ou suivis successifs d'une même surface.
La récolte réellement effectuée sera enregistrée dans le module Récoltes.

### Droits et cohérence

- L'agriculteur crée et modifie les fiches des parcelles auxquelles il est actuellement affecté.
  Plusieurs agriculteurs affectés à une même parcelle peuvent intervenir sur ces fiches.
- L'agronome consulte les fiches de ses parcelles ; l'administrateur consulte l'ensemble.
- La parcelle doit participer à la campagne choisie. Une campagne planifiée ou en cours accepte des fiches.
- La parcelle et la campagne d'une fiche enregistrée ne sont pas déplaçables ; ses autres informations restent modifiables.
- Une campagne clôturée bloque toute création ou modification de ses fiches, qui restent consultables.
- Le filtrage des listes et de leurs totaux se fait côté backend avant la pagination.
  Un retrait d'affectation retire aussi l'accès par URL directe et le droit de modification.
- Une variété déjà utilisée ne peut plus changer de culture ; son nom et sa description restent modifiables.
- Une parcelle portant une fiche ne peut pas être retirée de cette campagne.
  Les changements des dates de campagne ou de superficie de parcelle sont refusés s'ils invalident une fiche existante.

Des verrous courts sur la campagne, la parcelle et la variété protègent ces vérifications pendant
l'enregistrement. Une version interne protège aussi une fiche contre les modifications simultanées.
Les relations sont créées par Hibernate sans migration et sans suppression en cascade.

### API et interface

| Opération | API |
| --- | --- |
| Liste paginée autorisée | GET /api/plantations/pagination |
| Consultation | GET /api/plantations/{id} |
| Création par un agriculteur affecté | POST /api/plantations |
| Modification par un agriculteur affecté | PUT /api/plantations/{id} |

La liste accepte les filtres facultatifs `parcelleId`, `campagneId` et `statut`, ainsi que `page` et `taille`.
Elle est triée par identifiant décroissant.
Les routes Angular sont `/plantations`, `/plantations/nouvelle`, `/plantations/:id` et `/plantations/:id/modifier`.
Le menu latéral et l'accueil proposent l'accès aux plantations.
Le détail d'une parcelle affiche directement ses plantations avec filtres et pagination.
Le lien de création depuis cette parcelle la présélectionne dans le formulaire.
Le choix de la parcelle filtre les campagnes ouvertes ; celui de la culture filtre les variétés.
Les écrans sont chargés à leur ouverture, comme les campagnes.

### Vérifications de cette livraison

- 8 nouveaux tests backend : création pérenne, droits, rattachements, dates, superficies,
  clôture, retrait d'affectation, pagination filtrée et protection des données référencées.
  Bilan : **55 tests backend habituels réussis** ; les 17 tests de recette externe sont exécutés séparément.
- 10 nouveaux tests Angular : sélection dépendante, création, validations, lecture seule,
  droits des rôles, annulation des requêtes dépassées, intégration dans la parcelle,
  retrait d'accès et double soumission.
- Recette PostgreSQL et Keycloak : **17 tests réussis**, dont le parcours des plantations avec des JWT réels.
- Angular : **64 tests réussis**, compilation de production réussie, chargement initial de 495,39 kB sous le budget existant.

La recette réelle utilise des conteneurs temporaires supprimés à la fin du contrôle.
Les écrans ne sont pas encore validés visuellement dans le navigateur.

### Recette manuelle

1. Redémarrer le backend et ouvrir Angular.
2. Comme administrateur, vérifier qu'une culture Olivier, une variété Chemlali et une campagne ouverte avec sa parcelle existent.
3. Comme agriculteur affecté, ouvrir cette parcelle puis **Nouvelle plantation**.
4. Choisir sa campagne, Olivier et Chemlali ; saisir 2 ha et une date de plantation ancienne pour les arbres existants.
5. Enregistrer, consulter puis modifier la fiche. Tester une surface trop grande et une récolte prévue hors campagne.
6. Comme agronome affecté, vérifier la consultation sans action de modification.
7. Comme administrateur, clôturer la campagne : la fiche reste consultable sans modification possible.
8. Retirer l'affectation d'un agriculteur : vérifier la disparition des fiches de cette parcelle et le refus par URL directe.
9. Vérifier les formulaires, le tableau défilant et les filtres sur mobile.

La prochaine étape de développement est l'enregistrement des observations agricoles.
