# Météo OpenWeather — SmartAgri

Intégration backend et Angular du **12 novembre 2026**.

## Fournisseur retenu

OpenWeather a été retenu après la demande d'une API avec clé gratuite.
La clé sert aux API **Current Weather** et **5 day / 3 hour Forecast**.
La seconde fournit jusqu'à 40 échéances espacées de trois heures sur cinq jours.
Le backend utilise les coordonnées de l'exploitation pour les parcelles qui lui sont rattachées.

Documentation officielle consultée le 12 novembre 2026 :

- [Tarifs et offre gratuite](https://openweathermap.org/price) : 60 appels/minute et 1 million/mois pour les API concernées.
- [Météo actuelle](https://openweathermap.org/api/current) : température, humidité, date UTC et pluie sur une heure lorsqu'elle est fournie.
- [Prévisions à trois heures](https://openweathermap.org/api/forecast5) : température, humidité, probabilité de précipitations et pluie sur trois heures lorsqu'elles sont fournies.
- [Clé et activation](https://openweathermap.org/appid).
- [Licence et attribution](https://openweathermap.org/faq#license) : attribution et logo présents dans la vue météo.

Les quotas s'appliquent au compte. Un rechargement complet du point géographique effectue **deux appels fournisseur**.
L'intégration ne nécessite pas One Call, qui possède une tarification distincte.
Les conditions et les quotas du fournisseur pourront évoluer ; vérifier ces liens avant une mise en production.

## Configuration de la clé

Spring Boot lit `OPENWEATHER_API_KEY`. La clé réelle ne figure dans aucun fichier du projet.
Elle a été configurée dans les variables de l'utilisateur Windows.
Dans le terminal PowerShell utilisé pour le backend, récupérer cette valeur avant le démarrage :

```powershell
$env:OPENWEATHER_API_KEY = [Environment]::GetEnvironmentVariable("OPENWEATHER_API_KEY", "User")
mvn spring-boot:run
```

Les variables PostgreSQL et Keycloak restent nécessaires, comme expliqué dans le README.
Si le backend fonctionne déjà, l'arrêter puis le relancer depuis ce terminal pour prendre en compte la clé.

Pour un autre environnement, définir sa propre variable `OPENWEATHER_API_KEY` côté serveur.
La clé ne doit pas être envoyée à Angular. Le code n'affiche pas les URL d'erreur du fournisseur, car elles contiennent la clé.
Une clé absente permet quand même de démarrer la plateforme ; la consultation météo signale alors son indisponibilité.

## API SmartAgri

`GET /api/parcelles/{id}/meteo` nécessite un jeton Keycloak valide.

L'administrateur consulte toutes les parcelles ; l'agriculteur et l'agronome consultent leurs parcelles affectées.
Les contrôles d'affectation précèdent la lecture du cache et chaque appel fournisseur.
Une parcelle inconnue produit HTTP 404 ; une consultation non autorisée produit HTTP 401 ou 403.

Les situations météo sont renvoyées dans une réponse HTTP 200 avec un `statut` explicite :

| Statut | Signification |
| --- | --- |
| `DISPONIBLE` | Les données sont disponibles dans leur durée de fraîcheur. |
| `PERIMEE` | La mise à jour a échoué ; les dernières données connues sont disponibles avec leur date. |
| `INDISPONIBLE` | Aucune donnée exploitable ; le champ `donnees` est nul. |
| `LOCALISATION_MANQUANTE` | Une ou les deux coordonnées manquent ; aucun appel fournisseur. |
| `LOCALISATION_INVALIDE` | Les coordonnées existantes doivent être corrigées ; aucun appel fournisseur. |

La réponse contient la parcelle, l'exploitation, le point demandé, le statut, un message,
`source`, `lienSource` et éventuellement `donnees`.
Le frontend doit traiter le statut avant de présenter les valeurs.

`donnees` contient :

- `recupereesLe` : dernière récupération réussie, en UTC.
- `decalageUtcSecondes` : décalage communiqué par OpenWeather.
- `conditions` : date des données, température en °C, humidité en %, pluie sur la dernière heure en mm et description.
- `previsions` : dates UTC, température en °C, humidité en %, probabilité de précipitations en % et pluie sur trois heures en mm.

Les horodatages utilisent `Instant` et sont sérialisés en ISO UTC.
Le décalage fourni n'est pas un identifiant de fuseau IANA et ne décrit pas les futurs changements d'heure.
Les dates devront être affichées avec leur référence horaire explicite.
La probabilité OpenWeather, comprise entre 0 et 1, est convertie en pourcentage.
Les champs absents restent nuls : l'interface devra afficher « Non disponible » au lieu d'inventer un zéro.
La pluie sur une heure et celle sur trois heures restent des valeurs distinctes.
La météo représente une estimation au point de l'exploitation ; elle ne constitue pas une mesure sur chaque parcelle.

## Cache et pannes

Les paramètres se trouvent dans `backend/src/main/resources/application.properties` :

| Paramètre | Valeur par défaut |
| --- | --- |
| `app.meteo.base-url` | `https://api.openweathermap.org/data/2.5` |
| `app.meteo.connexion-secondes` | 3 |
| `app.meteo.lecture-secondes` | 5 par appel |
| `app.meteo.cache-secondes` | 900, soit 15 minutes |
| `app.meteo.secours-secondes` | 7 200, soit 2 heures depuis la récupération réussie |
| `app.meteo.attente-apres-echec-secondes` | 60 |

Le cache est en mémoire et partagé par coordonnées, avec une limite de 1 000 points.
Il est rechargé après redémarrage. Une modification de coordonnées utilise une autre entrée.
Une paire `(0, 0)` est valide. Les appels simultanés en attente d'un même chargement réutilisent son résultat.
Une panne entraîne une attente d'une minute avant de retenter le même point.
Les erreurs de clé, de quota, de réseau et les réponses invalides produisent un message explicite.
Les dernières données connues peuvent être affichées pendant la durée de secours, avec `PERIMEE` et leur date conservée.
Après cette durée, aucune donnée n'est renvoyée. Les prévisions déjà passées sont retirées de la réponse.

À la réception, une météo actuelle de plus de trois heures ou datée de plus de dix minutes dans le futur est refusée.
Les structures de réponse, les dates, l'ordre des prévisions, le décalage UTC, les nombres et leurs limites sont contrôlés.
Les futures règles d'alertes devront vérifier `DISPONIBLE` avant d'utiliser une valeur comme donnée fraîche.

## Affichage dans Angular

Ouvrir **Parcelles**, puis le détail d'une parcelle accessible : la météo apparaît après ses informations.
Les conditions et les prévisions sont chargées séparément des plantations.
Une erreur météo laisse les autres modules consultables.

Les prévisions sont présentées par pages de 10, 20 ou 50 entrées, sans requête supplémentaire au changement de page.
Les heures utilisent le décalage retourné par le backend, avec un libellé comme `UTC+01:00`.
Les dates des conditions et de la récupération sont affichées ; les données périmées ont un avertissement visible.
Une mesure absente affiche « Non disponible ».

Si la localisation manque ou doit être corrigée, l'administrateur dispose d'un lien vers le formulaire d'exploitation.
Les agriculteurs et agronomes disposent d'un message expliquant l'intervention de l'administrateur.
Un refus d'accès efface les anciennes données météo et les prévisions du composant.

Le logo officiel provient de la bibliothèque proposée dans la FAQ :
[archive des logos](https://openweathermap.org/payload/api/files/file/logo_files.zip),
fichier `Logo files/Master logo/RGB (for screen)/OpenWeather-Master-Logo RGB.png`.
Il est conservé dans `frontend/public/images/openweather-logo.png` et accompagné d'un lien vers OpenWeather.

La disposition utilise une colonne sur petit écran et plusieurs colonnes quand la largeur le permet.
La page utilise le défilement normal du document, sans hauteur de viewport ni `overflow-hidden`
sur le conteneur global. Sur ordinateur, la sidebar utilise `position: sticky` avec `top: 0`
et une hauteur de viewport ; sa navigation peut défiler si nécessaire. Le header suit le document.
Les composants de détail et de météo sont affichés en bloc ; la carte météo ne découpe pas son contenu
avec `overflow-hidden`. Un Firefox isolé avec une API fictive a été contrôlé aux largeurs 1920, 1280,
1117, 1024, 1000, 830 et 390 px, en redimensionnant la page sans rechargement : les conditions,
les dernières prévisions et les plantations sont accessibles. Le défilement du document et
la stabilité de la sidebar sur ordinateur sont également vérifiés.
Le défaut signalé ne se reproduit pas dans cette session isolée. Après la suppression du découpage global,
l'utilisateur a confirmé le bon fonctionnement dans sa session Firefox le 13 novembre 2026.

## Vérifications

Les tests habituels utilisent un fournisseur HTTP local et une horloge contrôlée pour les expirations.
La recette PostgreSQL/Keycloak conserve un fournisseur météo HTTP local afin de rester reproductible.
Une vérification réelle OpenWeather utilise séparément le point public de Sfax et la clé fournie.

Rapports générés localement :

- `backend/target/meteo-tests.log` : tests backend.
- `backend/target/recette-reelle.log` : recette PostgreSQL/Keycloak.
- `backend/target/meteo-live-tests.log` : contrôle optionnel OpenWeather réel.
- `backend/target/meteo-angular-tests.log` : tests des cartes météo et des parcours Angular.
- `backend/target/meteo-angular-build.log` : compilation Angular de production.

Pour relancer uniquement le contrôle réel depuis `backend`, avec une clé configurée :

```powershell
$env:OPENWEATHER_API_KEY = [Environment]::GetEnvironmentVariable("OPENWEATHER_API_KEY", "User")
$env:SMARTAGRI_LIVE_METEO_TEST = "true"
mvn -Dtest=OpenWeatherLiveTests test
Remove-Item Env:SMARTAGRI_LIVE_METEO_TEST
```

Les résultats chiffrés de la livraison figurent dans [SPRINT_3.md](SPRINT_3.md).
