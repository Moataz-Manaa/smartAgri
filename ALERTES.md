# Alertes agricoles

Les alertes de ce sprint sont des règles de suivi météo dans l'application.
Les seuils ci-dessous sont des paramètres initiaux de démonstration à adapter au contexte agricole.
Ils ne constituent pas des seuils agronomiques universels ni des prescriptions de traitement ou d'irrigation.

## Règles initiales

Les règles utilisent uniquement les prévisions OpenWeather dont l'échéance est strictement future
et située dans les prochaines 24 heures, borne supérieure incluse.

| Type | Avertissement | Critique | Donnée comparée |
| --- | --- | --- | --- |
| Chaleur | ≥ 35 °C | ≥ 40 °C | Température prévue à l'échéance |
| Gel | ≤ 0 °C | ≤ −3 °C | Température prévue à l'échéance |
| Pluie importante | ≥ 20 mm sur 3 heures | ≥ 40 mm sur 3 heures | Cumul de pluie du créneau fournisseur |

Une alerte critique remplace le niveau avertissement pour la même règle et la même échéance.
La pluie n'est pas additionnée entre plusieurs créneaux. La probabilité de pluie et l'humidité
ne sont pas utilisées comme des quantités de pluie. Une valeur absente ne vaut jamais zéro.
Le créneau de pluie couvre les trois heures précédant l'échéance ; la température est une valeur à cette échéance.
Les dates sont conservées en UTC. Le décalage d'affichage n'intervient pas dans les comparaisons.

## Cycle de vie et fraîcheur

Le calcul périodique s'exécute toutes les 30 minutes, avec un premier passage 60 secondes après démarrage.
Il utilise le cache météo partagé par coordonnées avec la consultation des parcelles.
La collecte météo s'effectue sans transaction ni verrou de base ; l'enregistrement utilise une transaction courte.

Seules les réponses `DISPONIBLE`, récupérées il y a moins de 15 minutes, sont évaluées.
L'âge contrôlé est celui de la récupération ; il ne représente pas une date d'entraînement ou d'émission du modèle.
Une panne, un statut périmé, une prévision absente ou une mesure absente/invalide ne lèvent pas une alerte.
Ils ne permettent pas de conclure à l'absence de risque. Les alertes précédentes conservent la date de leur source.

Une alerte est identifiée par la parcelle, le type, l'échéance et le point météo.
Une contrainte unique en base et un verrou court sur la parcelle empêchent les doublons.
Un nouveau calcul met à jour la même alerte ; il ne crée pas une nouvelle ligne à chaque passage.
Une réponse plus ancienne ne remplace pas une source plus récente déjà enregistrée.

- `ACTIVE` : le seuil était atteint lors de la dernière évaluation disponible et l'échéance n'est pas passée.
- `LEVEE` : une nouvelle valeur exploitable pour le même créneau n'atteint plus le seuil.
- `EXPIREE` : l'échéance est passée ou le point météo de l'exploitation a changé.

L'expiration s'effectue même si le fournisseur est en panne. Un changement de coordonnées rend les anciennes
alertes obsolètes ; un nouveau risque au nouveau point possède sa propre ligne et conserve son historique.
Une alerte levée peut redevenir active pour le même créneau si une nouvelle prévision franchit le seuil.
Le calcul n'efface pas l'historique et n'envoie aucun e-mail, SMS ou notification push.

## Configuration backend

Les propriétés `app.alertes.*` de `backend/src/main/resources/application.properties` permettent de régler
les six seuils, l'horizon, l'âge maximal des données et les délais de planification.
La validation refuse notamment des niveaux critiques moins sévères que les avertissements.
La planification peut être désactivée avec `app.alertes.planification-active=false`.
Les tests la désactivent et déclenchent le calcul explicitement avec une horloge contrôlée.

Le schéma des tables `alertes` et `lectures_alertes` est créé par Hibernate au redémarrage du backend, avec le réglage
`ddl-auto=update` déjà utilisé dans le projet. Aucune migration SQL n'est ajoutée.
Sans clé météo exploitable, le calcul ne crée pas de nouvelles alertes et conserve les expirations normales.
Chaque alerte conserve sa valeur, son seuil, son unité, son point géographique, son créneau,
la date de récupération météo et sa dernière vérification.

## API de consultation et de lecture

Les routes sont protégées par le filtre Keycloak existant. L'administrateur consulte toutes les alertes ;
les agriculteurs et agronomes consultent uniquement celles de leurs parcelles actuellement affectées.
Les listes, filtres et compteurs appliquent les mêmes droits côté backend.

| Méthode | Route | Résultat |
| --- | --- | --- |
| GET | `/api/alertes/pagination` | Page filtrée, sans marquage automatique comme lu |
| GET | `/api/alertes/{id}` | Détail avec données sources et lecture du compte connecté |
| GET | `/api/alertes/compteur` | `activesNonLues` et `calculeLe`, avec `parcelleId` facultatif |
| PUT | `/api/alertes/{id}/lecture` | Marquage personnel comme lu, sans corps de requête |

Les filtres de liste sont tous facultatifs : `parcelleId`, `type`, `niveau`, `statut`, `lue`, `dateDebut`, `dateFin`.
Les dates `YYYY-MM-DD` portent sur l'échéance météo `datePrevision`, par journées UTC inclusives,
entre les années 1 et 9999. La date de début ne peut pas suivre la date de fin.
Sans filtre de statut, la liste inclut l'historique. Les états sont `ACTIVE`, `LEVEE`, `EXPIREE`.
La pagination utilise `page` à partir de 0 et `taille` entre 1 et 100, avec 10 lignes par défaut.
Le tri est `dateDetection` décroissante, puis identifiant décroissant pour stabiliser les pages.
Les filtres et les droits sont appliqués avant le comptage et la pagination en base.

Exemple de consultation :

```text
GET /api/alertes/pagination?statut=ACTIVE&lue=false&type=CHALEUR&page=0&taille=10
```

La réponse de détail contient la parcelle, l'exploitation, le type, le niveau, la valeur, le seuil,
l'unité, le point météo, les dates sources, `statut`, `donneesRecentes`, `lue` et `dateLecture`.
Les identités Keycloak et les lectures des autres comptes ne sont pas exposées.
L'API ne contacte pas OpenWeather pour consulter les alertes et un GET ne modifie pas leur stockage.

Le statut exposé tient immédiatement compte d'une échéance passée ou d'un changement de coordonnées,
même avant le prochain calcul planifié. Les filtres et compteurs utilisent cette même définition.
Si le calcul planifié n'a pas encore enregistré la clôture, `dateCloture` peut rester nulle malgré
le statut effectif `EXPIREE` ; `motifCloture` explique alors l'échéance passée ou le point modifié.
`donneesRecentes` indique uniquement l'âge de la récupération météo selon le délai configuré.
Une source ancienne peut rester une alerte connue active jusqu'à son échéance : elle n'est pas présentée comme fraîche.

La lecture est enregistrée dans `lectures_alertes` avec une contrainte unique alerte/compte.
Le compte est déterminé par l'identité authentifiée ; aucun identifiant d'utilisateur fourni par le client n'est utilisé.
Deux marquages répétés ou simultanés conservent une seule lecture et sa première date.
Cette lecture ne lève pas l'alerte et ne change pas celle des autres comptes.
Une réévaluation du même identifiant, y compris un changement de niveau, conserve la lecture ;
un nouveau créneau crée une autre alerte initialement non lue.
Le retrait d'affectation bloque immédiatement les lectures et les compteurs concernés ;
une réaffectation au même compte retrouve son historique de lecture.

Les erreurs suivent les conventions du projet : 400 pour les paramètres invalides, 401 sans session valide,
403 pour une parcelle non autorisée et 404 pour une ressource introuvable.

## Interface Angular

Le menu latéral « Alertes » ouvre `/alertes` pour les trois rôles. Un lien dans le détail d'une parcelle
préremplit le filtre de parcelle. La liste affiche les alertes actives par défaut, puis permet de filtrer
par parcelle, type, niveau, état, lecture personnelle et journées d'échéance UTC inclusives.
Les filtres s'appliquent avec « Filtrer ». La pagination utilise les filtres appliqués, même si le formulaire
contient des modifications pas encore soumises. « Réinitialiser les filtres » restaure les valeurs initiales.

Le compteur « Alertes actives non lues » porte sur toutes les parcelles accessibles ou sur la parcelle
sélectionnée. Ce périmètre est indiqué : les filtres de type, niveau, état et dates ne limitent pas ce compteur.
Une panne du compteur n'empêche pas la consultation de la liste et n'affiche pas un faux zéro.

Le détail `/alertes/:id` présente la valeur prévue, le seuil, les unités, le point de l'exploitation,
la source et les dates en UTC. Une source ancienne est signalée séparément du statut de l'alerte.
Consulter le détail ne marque pas l'alerte comme lue. « Marquer comme lue » enregistre la lecture du compte
connecté après confirmation de l'API. L'action ne lève pas l'alerte et ne modifie pas les autres comptes.
Le retour à la liste conserve les filtres et la pagination, puis recharge la liste et le compteur.

Les changements de contexte annulent les requêtes en cours et effacent les anciennes données.
Un refus d'accès retire les données concernées de l'écran. Aucun bouton Actualiser n'est présent.
Les écrans gardent le défilement naturel du document et la navigation latérale existante.

## Vérifications du 14 novembre 2026

Les 162 tests backend habituels réussissent, dont 19 tests de calcul et de persistance,
3 de validation des règles et un nouveau contrôle du cache partagé entre l'API et la génération interne.
Les tests couvrent également deux calculs concurrents, la contrainte unique, les unités, les pannes,
les données absentes et périmées, les changements de point et l'expiration à l'échéance.
La recette PostgreSQL/Keycloak temporaire réussit avec 27 tests, dont le stockage et la réévaluation d'alertes
sur une parcelle créée par l'API. Aucun appel OpenWeather réel n'est nécessaire pour ces tests.
Rapports : `backend/target/alertes-tests.log` et `backend/target/recette-reelle.log`.

Après ajout de l'API : **184 tests backend habituels réussis**, dont 21 tests API nouveaux et un contrôle
supplémentaire des coordonnées égales à zéro. Les tests API couvrent les trois rôles, les lectures personnelles,
les filtres, la pagination, la concurrence, la désactivation d'un compte et le retrait d'affectation.
Rapport complet : `backend/target/alertes-api-complets-tests.log`.
La recette étendue PostgreSQL/Keycloak compte **28 tests réussis**. Elle vérifie les routes avec de vrais comptes,
les filtres combinés, la lecture personnelle répétée, les compteurs, le retrait d'affectation et la réaffectation.
Les deux conteneurs et leur stockage temporaires ont été supprimés en fin de contrôle.

Après ajout de l'interface : **173 tests Angular réussis**, dont **25 nouveaux tests d'alertes**.
La compilation de production réussit avec 494,98 kB de chargement initial (budget : 500 kB).
La liste a été contrôlée dans un Firefox isolé pour les trois rôles aux largeurs 1920, 1280, 1024,
1000 et 390 px, et le détail à 1920 et 390 px. Les captures et les mesures ne montrent ni débordement
horizontal ni contenu masqué. Le parcours filtrage, détail, lecture explicite et retour a également réussi.
Ces contrôles visuels utilisent des données et une authentification fictives ; les autorisations réelles
restent couvertes par les tests backend et la recette PostgreSQL/Keycloak décrits plus haut.
Rapports : `backend/target/alertes-angular-complets-tests.log`, `backend/target/alertes-angular-build.log`
et `backend/target/alertes-ui-firefox.log`.
