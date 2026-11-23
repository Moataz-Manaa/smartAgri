# Tableaux de bord — Sprint 3

## Accès et organisation

L'accueil conserve les raccourcis propres à chaque rôle et ajoute un **Bilan agricole**.
L'administrateur voit l'ensemble des parcelles de la société ; l'agriculteur et l'agronome voient
uniquement les parcelles de leurs affectations actuelles. L'agronome dispose de liens de consultation,
sans action d'enregistrement agricole dans le bilan.

Le bloc **Organisation actuelle** reste distinct du bilan : les compteurs d'exploitations, parcelles,
utilisateurs et affectations ne dépendent pas de ses filtres. Les utilisateurs et affectations
restent réservés à l'administrateur. Le bilan ne crée ni diagnostic ni prédiction Machine Learning.

## Périmètre des indicateurs

| Indicateur | Calcul et périmètre |
| --- | --- |
| Parcelles du périmètre | Parcelles actuellement accessibles, limitées à la campagne si sélectionnée. Ce compteur ne dépend pas des dates. |
| Campagnes sur la période | Campagnes associées à au moins une parcelle du périmètre et dont les dates recoupent la période, bornes inclusives. Tous les statuts sont inclus. |
| Plantations suivies | Fiches de suivi des campagnes ci-dessus, dans les parcelles du périmètre. Tous les statuts sont inclus. La date de plantation ne limite pas le comptage : un olivier planté avant la campagne reste suivi. |
| Activités enregistrées | Nombre d'observations, irrigations, fertilisations, traitements et récoltes des fiches suivies, dont la date est dans la période. |
| Eau utilisée pour l'irrigation | Somme des volumes normalisés en m³ des irrigations datées dans la période. 500 L et 2 m³ donnent 2,5 m³. |
| Quantité récoltée | Somme des quantités normalisées en kg des récoltes datées dans la période. 100 kg et 1 tonne donnent 1 100 kg. |
| Activités récentes | Les cinq dernières activités du même périmètre et de la même période. Tri par date décroissante, puis type croissant et identifiant décroissant. |
| Alertes météo actives | Alertes des parcelles du périmètre, encore actives à l'instant du calcul, dont l'échéance UTC appartient à la période. |
| Alertes actives non lues | Parmi ces alertes, celles que le compte connecté n'a pas marquées comme lues. |

Une parcelle partagée entre plusieurs personnes n'est comptée qu'une fois. Une campagne associée
à plusieurs parcelles accessibles n'est comptée qu'une fois. Les identifiants d'activités identiques
dans deux tables restent deux activités différentes. Les cumuls d'irrigation et de récolte sont
calculés séparément, sans multiplication par les autres opérations ni par les affectations.
Les rendements de plantations différentes ne sont pas additionnés.

Une campagne sans plantation peut donc compter comme une campagne, avec zéro fiche et zéro activité.
Une combinaison autorisée de parcelle et campagne sans intersection renvoie des résultats vides.
Une personne sans affectation reçoit des zéros et des listes vides.

Les alertes concernent une parcelle et sa météo, indépendamment des plantations : le filtre de campagne
sélectionne ses parcelles associées. Il n'attribue pas une alerte à une plantation ou à une récolte.
La définition de l'état actif est celle de [l'API des alertes](ALERTES.md) : échéance future,
statut enregistré actif et coordonnées identiques au point actuel de l'exploitation.
Une échéance passée ou un point modifié est exclu immédiatement, sans attendre la tâche planifiée.
La lecture reste personnelle. Un compteur nul ne garantit pas l'absence de risque météo.

## API

Les trois routes sont des lectures authentifiées, sans écriture ni appel OpenWeather :

| Méthode | Route | Réponse |
| --- | --- | --- |
| GET | `/api/tableau-de-bord/synthese` | Période appliquée, compteurs et cumuls normalisés. |
| GET | `/api/tableau-de-bord/activites` | Liste de zéro à cinq activités avec type, date, description, parcelle, campagne et identifiant de plantation. |
| GET | `/api/tableau-de-bord/alertes` | `actives`, `activesNonLues` et `calculeLe`. |

Les trois routes acceptent les mêmes paramètres facultatifs :

- `parcelleId` et `campagneId` : entiers strictement positifs.
- `dateDebut` et `dateFin` : dates ISO `YYYY-MM-DD`, entre les années 1 et 9999, début inférieur ou égal à fin.
- En l'absence de dates, le début vaut la date UTC du jour moins 29 jours et la fin vaut la date UTC du jour.
  Si une seule borne est fournie, l'autre prend sa valeur par défaut ; la période résultante doit rester valide.

Exemple :

```http
GET /api/tableau-de-bord/synthese?parcelleId=11&campagneId=3&dateDebut=2026-11-01&dateFin=2026-11-30
```

Les dates d'activité et de campagne sont des dates civiles, sans conversion de fuseau horaire.
Les dates filtrant les échéances d'alertes délimitent des journées UTC inclusives.
`calculeLe` est un instant présenté en UTC. Pour inclure les alertes prévues demain, la date de fin
doit inclure demain : le bilan commence par les trente derniers jours, jusqu'à aujourd'hui.

Le backend détermine l'identité et le rôle à partir du compte Keycloak vérifié à chaque appel.
Un identifiant de compte ou un rôle fourni par le navigateur ne peut pas changer le périmètre.
Le retrait d'affectation limite les résultats dès l'appel suivant avec le même JWT.
Un filtre explicite sur une parcelle ou campagne non autorisée est refusé, même sans activité dans la période.

Erreurs : 400 pour un filtre invalide, 401 sans session valide ou pour un compte désactivé,
403 pour un périmètre non autorisé, 404 pour une parcelle ou campagne introuvable.
Les paramètres SQL sont liés séparément ; aucune valeur fournie par l'utilisateur n'est concaténée au SQL.

## Interface Angular

Les modèles et le service HTTP sont dans `core/models` et `core/services`.
Le composant `features/tableau-de-bord` est intégré à l'accueil. La route d'accueil est chargée
à la demande pour conserver le budget de chargement initial du projet.

Le formulaire distingue les valeurs modifiées des filtres effectivement appliqués.
« Appliquer les filtres » recharge les trois blocs ; « 30 derniers jours » restaure la période initiale
et retire les filtres de parcelle et campagne. Les choix sont obtenus depuis les API existantes,
déjà filtrées par les droits du compte. Les champs gardent une bordure, une hauteur de 48 px
et un focus visible sur ordinateur comme sur mobile.

Le bilan, les activités récentes et les alertes se chargent indépendamment. Une panne d'un bloc
ne masque pas les deux autres et ne produit pas de faux zéro. Une panne d'une liste de choix
désactive seulement le filtre concerné ; les autres filtres restent utilisables.
Un refus d'accès efface les données du bilan et annule les réponses en cours.
Les anciens appels sont aussi annulés au changement de filtre et à la destruction du composant.
Aucun bouton Actualiser n'est ajouté. Le défilement naturel et la navigation latérale sont conservés.

Le lien d'une activité ouvre sa fiche de suivi. Le lien « Toutes les alertes accessibles » ouvre
la liste générale des alertes : il ne conserve pas les filtres du bilan, notamment le filtre de campagne.
La météo reste consultable dans le détail de chaque parcelle.

## Vérification manuelle

1. Ouvrir l'accueil avec chacun des trois rôles et vérifier les parcelles accessibles.
2. Choisir une période contenant des opérations connues, puis une parcelle et une campagne.
3. Comparer les volumes en m³ et les récoltes en kg avec les opérations enregistrées.
4. Ouvrir une activité récente et consulter sa fiche de suivi.
5. Vérifier les alertes de la période, marquer une alerte comme lue puis rouvrir l'accueil.
6. Retirer une affectation et vérifier les nouvelles valeurs avec le même compte connecté.
7. Vérifier l'affichage sur ordinateur et mobile et l'accès à la dernière activité par défilement.

Les vérifications automatisées et leurs rapports sont consignés dans [le plan du sprint 3](SPRINT_3.md)
et [la recette finale](RECETTE_SPRINT_3.md).
