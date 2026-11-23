# Recette du sprint 3 — SmartAgri

Recette et clôture technique : **14 novembre 2026**.

**Sprint 3 terminé techniquement — 30/30 tâches, soit 100 %.**

Le sprint couvre la localisation météo, les conditions et prévisions OpenWeather,
la génération des alertes, leur lecture personnelle et les tableaux de bord agricoles.
Le Machine Learning, les diagnostics et leur validation, les prédictions, le DevOps et le déploiement
restent au sprint 4.

## Résultats

| Vérification | Résultat | Rapport |
| --- | --- | --- |
| Tests backend habituels et création du JAR Spring Boot | 200 tests réussis ; JAR créé | `backend/target/sprint3-livraison-backend.log` |
| Tests Angular | 189 tests réussis | `backend/target/sprint3-livraison-angular-tests.log` |
| Compilation Angular de production | Réussie ; 485,08 kB de chargement initial, sous le budget de 500 kB | `backend/target/sprint3-livraison-angular-build.log` |
| Recette PostgreSQL/Keycloak réels | 30 tests réussis, aucun échec ni test ignoré | `backend/target/recette-reelle.log` |
| Parcours navigateur avec données fictives | 3 rôles × 6 largeurs × 4 écrans, soit 72 contrôles de vue, plus le parcours et les états dégradés | `backend/target/sprint3-livraison-ui.log` et `backend/target/recette-sprint3-ui/resultats.json` |

Les tests Maven habituels comptent 231 cas découverts : 200 exécutés avec succès et 31 ignorés volontairement.
Ces derniers correspondent aux 30 tests PostgreSQL/Keycloak exécutés séparément et au test optionnel
OpenWeather réel, non exécuté lors de cette clôture. Le fournisseur externe est simulé par un serveur HTTP local
pendant la recette ; aucune clé réelle ni requête OpenWeather n'est nécessaire.

Les rapports, captures et compilations sont générés localement dans des dossiers ignorés par Git.
Les conteneurs et leur stockage temporaires ont été supprimés après la recette PostgreSQL/Keycloak.
Le navigateur de recette utilise son propre profil et ses propres ports ; les processus créés ont été arrêtés.
Les services et données habituels ne sont pas utilisés par ces recettes.

## Portée de la validation

Les autorisations, JWT, comptes, lectures personnelles et persistances sont vérifiés avec **PostgreSQL et Keycloak réels**.
Les routes backend sont appelées via MockMvc, avec les contrôleurs, le filtre JWT, les services et les repositories du projet.
L'horloge météo peut être avancée dans le test de parcours complet ; la validation des JWT reste réelle.
Le service de génération utilisé par la tâche planifiée est appelé directement : la recette n'attend pas son intervalle de 30 minutes.

Le contrôle visuel exécute **l'application Angular réelle dans Firefox**, avec une **API et une authentification fictives**
sur un serveur local isolé. Le script transforme uniquement les réponses JavaScript de ce serveur de test ;
il ne modifie pas les fichiers de l'application ni la configuration Keycloak.
Il ne constitue donc pas une connexion navigateur complète à Keycloak et PostgreSQL.

L'agent a examiné des captures sur ordinateur et mobile. Les contrôles mesurent aussi le débordement horizontal
et vérifient que les derniers éléments restent atteignables et visibles par défilement.
L'utilisateur avait confirmé le correctif de défilement météo le 13 novembre 2026 ;
aucune validation personnelle de sa part n'est revendiquée pour les nouveaux écrans d'alertes et de bilan.

## Parcours intégré PostgreSQL/Keycloak

Le test `parcoursCompletSprintTroisAvecPanneFraicheurLectureEtRetraitReels` vérifie les étapes suivantes :

1. Créer une exploitation avec ses coordonnées, une parcelle, deux agriculteurs et un agronome réellement connectés.
2. Affecter les trois personnes à la parcelle et consulter sa météo par l'API.
3. Recevoir une prévision HTTP locale fictive à 40 °C et 40 mm sur trois heures.
4. Exécuter deux fois le service de génération : deux alertes sont stockées, sans doublon et sans nouvelle collecte grâce au cache.
5. Consulter les alertes et leur compteur dans le tableau de bord avec les trois rôles.
6. Ouvrir un détail sans lecture automatique, puis marquer une alerte comme lue pour le premier agriculteur.
   Son compteur passe de deux à une non-lue ; l'agronome conserve sa propre lecture.
7. Avancer l'horloge météo de 16 minutes et provoquer une réponse fournisseur HTTP 503.
   Les dernières données sont présentées comme périmées, les alertes connues restent actives et leur source est signalée ancienne.
   La lecture personnelle reste enregistrée et les indicateurs agricoles restent disponibles.
8. Dépasser les deux heures de secours : la météo devient indisponible, avec des données nulles,
   sans inventer de mesures et sans lever les alertes avant leur échéance.
9. Retirer uniquement l'affectation du premier agriculteur. Avec le même JWT, météo, détail, lecture et filtres
   du tableau de bord sont refusés avant tout appel fournisseur ; le second agriculteur conserve l'accès.
10. Réaffecter le premier agriculteur : son historique de lecture est retrouvé.
11. Atteindre exactement l'échéance de prévision : le détail expose immédiatement l'expiration,
    la génération la persiste et le compteur des alertes actives revient à zéro.

Les autres tests de recette complètent ce parcours avec les données agricoles, les filtres de campagne,
les unités mélangées, les parcelles partagées, les lectures répétées et les contrôles de compte.
Les tests habituels couvrent les seuils inclusifs, les prévisions manquantes, les quotas et délais fournisseur,
les réponses anciennes ou arrivées en retard, les changements de point, la concurrence et les cas de localisation invalide.

## Parcours Firefox

Rôles : **ADMIN**, **AGRICULTEUR**, **AGRONOME**.
Largeurs : **320, 390, 768, 1024, 1280 et 1920 px**, avec une hauteur de 900 px.

Pour chaque combinaison, le script :

1. Ouvre le détail d'une parcelle et vérifie la météo, la dernière prévision de la page, le pied météo et l'accès aux plantations.
2. Utilise le lien vers les alertes de cette parcelle et vérifie le filtre transmis.
3. Ouvre le détail d'une alerte, vérifie les données sources et l'absence de marquage automatique.
4. Marque explicitement l'alerte comme lue, puis revient à la liste avec son état de lecture.
5. Rejoint l'accueil par la navigation latérale ; sur mobile, ouvre le menu puis vérifie sa fermeture après navigation.
6. Vérifie les cumuls d'irrigation et de récolte, la dernière activité et le compteur personnel actualisé.
7. Vérifie l'absence de bouton Actualiser et de débordement horizontal.

Le script vérifie ensuite l'affichage des dernières données périmées, la disparition des mesures
en état indisponible, puis le retrait des données de détail et de bilan après un refus d'accès.
Les captures des quatre écrans, à 390 et 1920 px, sont conservées dans
`backend/target/recette-sprint3-ui/`.

Les contrôles de filtres combinés, pagination, erreurs indépendantes, annulation de requêtes et gardes
sont aussi couverts par les tests Angular. La recette ne remplace pas un audit d'accessibilité exhaustif
ou une mesure de charge en production.

## Reproduire la recette

Prérequis : Java 21, Maven, Node compatible avec Angular, dépendances installées, Python,
Docker Desktop démarré et les images PostgreSQL/Keycloak disponibles localement.
Le script navigateur nécessite Firefox et Node avec l'API WebSocket globale (Node 22 ou supérieur).
Les détails des services temporaires figurent dans [la documentation Keycloak](keycloak/README.md).

Depuis `backend` :

```powershell
mvn package
```

Depuis `frontend` :

```powershell
npm test -- --watch=false
npm run build
npm run recette:sprint3:ui
```

Si Firefox est installé ailleurs :

```powershell
$env:SMARTAGRI_FIREFOX_PATH="C:\chemin\vers\firefox.exe"
npm run recette:sprint3:ui
```

Depuis la racine du projet :

```powershell
python keycloak/verify_backend.py
```

Ce dernier script utilise Maven hors ligne et le cache du profil courant, puis supprime ses conteneurs temporaires.
Le script Firefox démarre son propre serveur Angular sur un port libre ; il n'est pas nécessaire de lancer
les services habituels pour cette vérification. Pour le lancement normal, conserver `mvn spring-boot:run`
et `ng serve` : voir [le README](README.md).

## Vérification complémentaire dans la session habituelle

Cette liste permet de reproduire le parcours avec les comptes et données de démonstration de l'utilisateur :

- Vérifier les coordonnées réelles de l'exploitation : la météo correspond à ce point, et non à une mesure de la parcelle.
- Consulter les conditions, les dates, les unités et les prévisions à trois heures.
- Ouvrir les alertes de la parcelle ; une liste vide est normale si aucun seuil n'est atteint.
  Le calcul démarre après une minute puis se répète toutes les 30 minutes selon la configuration.
- Vérifier la lecture personnelle avec deux comptes et le compteur de l'accueil, en incluant l'échéance dans la période choisie.
- Comparer le bilan à des irrigations et récoltes connues ; vérifier la période, la campagne et la parcelle appliquées.
- Retirer une affectation et provoquer un nouvel appel : les données déjà affichées ne disparaissent pas par notification instantanée,
  mais le backend refuse chaque nouvel accès et l'écran efface ses données au refus.
- Vérifier le menu mobile et le défilement jusqu'à la dernière prévision ou activité.

Les règles météo et leurs limites sont détaillées dans [ALERTES.md](ALERTES.md),
les unités et périodes du bilan dans [TABLEAU_DE_BORD.md](TABLEAU_DE_BORD.md),
et la configuration fournisseur dans [METEO.md](METEO.md).
