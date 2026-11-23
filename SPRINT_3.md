# Sprint 3 — Météo, alertes et tableaux de bord

## Objectif

Permettre aux agriculteurs et aux agronomes de consulter la météo de leurs parcelles,
de suivre les alertes agricoles et de disposer d'indicateurs adaptés à leur rôle.
L'administrateur conserve une vue globale de la plateforme.

Le sprint 2 est clôturé : [plan](SPRINT_2.md) et [recette](RECETTE_SPRINT_2.md).
Le Machine Learning, la validation des diagnostics, les prédictions, le DevOps et le déploiement restent au sprint 4.

## Avancement

Au **14 novembre 2026 : 100 %**, soit **30 tâches terminées sur 30**.
Le sprint 3 est clôturé techniquement : météo, alertes, lecture personnelle et tableaux de bord sont vérifiés.
Les preuves, commandes et limites de la validation sont consignées dans [la recette finale](RECETTE_SPRINT_3.md).
La prochaine étape est la préparation du sprint 4 : Machine Learning et DevOps.
Le pourcentage correspond aux cases cochées du plan, arrondi à l'entier, et non au temps de développement.
Il sera actualisé après chaque livraison vérifiée.

## Conventions conservées

- Spring Boot, Java 21 et Lombok, avec un code simple et des conversions explicites.
- Interfaces dans `service`, implémentations dans `service/impl` et DTO par domaine : `meteo`, `alerte`, `tableaudebord`.
- Keycloak pour les identités ; contrôles métier côté backend, sans Spring Security.
- PostgreSQL et schéma géré par Hibernate, sans migrations.
- Angular avec `core/models`, `core/services`, `core/guards`, `features`, `layout` et `shared`.
- Styles Tailwind/CSS cohérents, champs lisibles, navigation latérale et affichage mobile.
- Pagination partagée, messages de chargement, états vides et erreurs explicites.
- Aucun bouton Actualiser. Les chargements et éventuels recalculs restent contrôlés.
- Tests métier pertinents, tests Angular et recette avec PostgreSQL/Keycloak temporaires.

## Fonctionnement prévu

### Localisation et météo

Les coordonnées existent déjà dans `Exploitation`. Une parcelle utilisera la localisation de son exploitation.
L'interface devra préciser que la météo correspond à ce point géographique, et non à une mesure sur la parcelle.
L'administrateur seul pourra modifier cette localisation.
Une exploitation sans coordonnées restera utilisable, avec un message « Localisation météo non renseignée ».
Une latitude ou longitude égale à zéro est une coordonnée valide ; les deux valeurs doivent être présentes ensemble.

Le fournisseur retenu est **OpenWeather**, après la demande d'une API populaire avec clé gratuite.
Les API Current Weather et 5 day / 3 hour Forecast sont utilisées, avec l'offre gratuite documentée au 12 novembre 2026.
La clé fournie a été configurée dans les variables de l'utilisateur Windows et testée avec succès.
Les conditions, les quotas et l'attribution sont consignés dans [METEO.md](METEO.md).
Le backend appellera le fournisseur ; Angular consultera uniquement l'API SmartAgri.

L'affichage visera la température, l'humidité, les précipitations et les prévisions de pluie,
selon les données réellement disponibles. Les unités, la date des données, le fuseau et la dernière récupération seront indiqués.
Un cache court limitera les appels répétés. Les données périmées seront identifiées ; une panne ne produira pas de fausses valeurs nulles ou égales à zéro.
Les délais d'attente, les quotas et les erreurs externes devront être traités sans bloquer le reste de l'application.

### Alertes agricoles

Le premier périmètre prévoit des alertes dans l'application, sans e-mail, SMS ni notification push.
Les premières règles porteront sur les conditions météo disponibles, par exemple la chaleur ou des pluies importantes.
Leurs seuils, unités, périodes et niveaux seront explicités avant implémentation.
Ils seront configurables côté backend et présentés comme des règles de suivi, sans prétendre constituer des recommandations agronomiques universelles.

Une alerte sera rattachée à une parcelle et contiendra un type, un niveau, un message,
les dates pertinentes et les données ayant déclenché la règle.
Une réévaluation de la même règle sur la même parcelle et la même période ne devra pas créer de doublon.
Les données météo absentes ou trop anciennes ne permettront pas de conclure à une absence de risque.
Les alertes expirées resteront distinguées des alertes actives.

La lecture sera personnelle : lorsqu'un utilisateur marque une alerte comme lue,
elle restera non lue pour les autres utilisateurs concernés.
Le retrait d'affectation devra bloquer l'accès aux alertes et à leurs compteurs dès le prochain appel API.

### Tableaux de bord

L'accueil existant sera enrichi, en conservant les menus et raccourcis par rôle.
Les agriculteurs et agronomes verront uniquement les indicateurs issus de leurs affectations actuelles.
L'administrateur disposera des totaux globaux, sans avoir besoin d'affectation.
Un agronome consultera les données ; il ne pourra pas enregistrer des opérations agricoles à la place d'un agriculteur.

Les indicateurs visés concernent les parcelles, campagnes, plantations, activités récentes,
alertes actives/non lues, volumes d'irrigation et récoltes.
Les agrégations seront calculées côté backend, sans doublonner une parcelle partagée entre plusieurs utilisateurs.
Les volumes seront normalisés en m³ et les récoltes en kg avant addition.
Les compteurs et cumuls indiqueront leur périmètre et leur période.
Les rendements de plantations différentes ne seront pas additionnés ; le rendement par plantation reste disponible dans son détail.
Les cartes météo, d'alertes et les indicateurs métier devront pouvoir signaler leurs erreurs séparément.

## Plan de travail

### Étape 1 — Préparer le sprint

- [x] Examiner les coordonnées des exploitations et les indicateurs d'accueil existants.
- [x] Définir le périmètre, les règles d'accès et la séparation avec le sprint 4.
- [x] Consigner le plan, les conventions et le calcul d'avancement.

### Étape 2 — Préparer la localisation

- [x] Vérifier et compléter les validations backend des coordonnées des exploitations.
- [x] Adapter leur formulaire et leur détail pour expliquer la localisation utilisée par la météo.
- [x] Tester les coordonnées manquantes, partielles, nulles et hors limites, ainsi que les droits de modification.

### Étape 3 — Intégrer la météo côté backend

- [x] Choisir et documenter le fournisseur, ses conditions et les champs disponibles.
- [x] Créer les DTO météo, le service avec interface/implémentation et le client du fournisseur.
- [x] Exposer la météo par parcelle avec contrôle d'accès, validation de localisation et unités explicites.
- [x] Gérer cache, fraîcheur, délais d'attente et erreurs ; tester les réponses externes et les autorisations.

### Étape 4 — Afficher la météo dans Angular

- [x] Ajouter les modèles, le service et les composants météo dans l'architecture existante.
- [x] Afficher les conditions et prévisions avec unités, dates, localisation et états indisponibles.
- [x] Vérifier les parcours Angular et la présentation sur ordinateur et mobile.

### Étape 5 — Définir et calculer les alertes

- [x] Consigner les premières règles, seuils, périodes, niveaux et limites d'interprétation.
- [x] Implémenter les alertes par parcelle, leur génération périodique et leur expiration, sans doublons.
- [x] Tester les limites des règles, les réévaluations, les données périmées et les pannes météo.

### Étape 6 — Exposer les alertes et leur lecture

- [x] Ajouter les DTO, repositories, services et contrôleurs avec accès selon les affectations.
- [x] Ajouter la liste paginée et les filtres de parcelle, type, niveau, état et période.
- [x] Implémenter la lecture personnelle et le compteur de non-lues sans modifier l'état des autres utilisateurs.
- [x] Tester le partage des parcelles, les filtres, les compteurs et le retrait d'affectation.

### Étape 7 — Réaliser les écrans d'alertes

- [x] Créer la liste, les filtres et le détail avec un accès depuis la navigation.
- [x] Afficher les niveaux, dates, données sources et états de lecture avec une action accessible.
- [x] Tester les parcours Angular, les refus d'accès et les vues ordinateur/mobile.

### Étape 8 — Enrichir les tableaux de bord

- [x] Implémenter les agrégations backend et leurs filtres de période, campagne et parcelle selon le rôle.
- [x] Enrichir l'accueil avec des indicateurs lisibles, des raccourcis et des erreurs indépendantes.
- [x] Tester les périmètres, les unités, les doublons d'affectation et les trois rôles dans l'API et Angular.

### Étape 9 — Recette et livraison

- [x] Vérifier le parcours complet météo, génération d'alertes, lecture personnelle et tableaux de bord.
- [x] Vérifier la panne fournisseur, la fraîcheur et la perte d'accès après retrait d'affectation.
- [x] Exécuter les tests, les compilations et la recette avec PostgreSQL/Keycloak temporaires.
- [x] Valider les écrans sur ordinateur/mobile et consigner la recette du sprint 3.

## Première livraison — Préparation

Le 10 novembre 2026, le code existant a été consulté et le plan a été créé.
Les exploitations possèdent déjà `localisation`, `latitude` et `longitude`.
Les parcelles sont rattachées à une exploitation et les accès sont contrôlés par les affectations.
L'accueil présente déjà des compteurs administratifs et des totaux accessibles aux rôles métier.

Aucune fonctionnalité météo ou alerte n'avait encore été ajoutée à cette première livraison.

## Deuxième livraison — Localisation des exploitations

Livraison du **10 novembre 2026**. Les coordonnées restent facultatives mais doivent être renseignées ensemble.
Une exploitation peut conserver deux valeurs absentes, ou une paire complète.
Le backend refuse les coordonnées partielles avant toute écriture, à la création comme à la modification.
Les limites inclusives restent [-90, 90] pour la latitude et [-180, 180] pour la longitude.
Les valeurs non finies sont refusées et la paire `(0, 0)` est valide.
L'administrateur peut compléter, corriger ou effacer les deux valeurs.
Les agriculteurs et agronomes affectés peuvent les consulter sans les modifier.

Le formulaire explique le point météo utilisé et vérifie la présence des deux champs avant l'envoi.
Le détail distingue les coordonnées renseignées d'une localisation météo absente.
Il indique que ce point sera utilisé pour les parcelles de l'exploitation, sans présenter la météo comme une mesure locale.
Les anciennes exploitations dépourvues de coordonnées restent utilisables.
Le formulaire se charge à son ouverture, comme les autres formulaires récents, afin de respecter le budget Angular.

Vérifications : **113 tests backend habituels** et **131 tests Angular** réussis.
Les six nouveaux tests backend couvrent l'absence des deux valeurs, zéro, les paires partielles,
les limites inclusives, les valeurs hors limites ou non finies, la correction, l'effacement et les droits.
Les dix nouveaux tests Angular couvrent la saisie, la modification, les messages du détail et les gardes par rôle.
La compilation de production réussit avec **490,76 kB** de chargement initial, sous le budget de 500 kB.
La recette avec PostgreSQL et Keycloak réels compte **25 tests réussis**.
Le nouveau parcours vérifie la persistance de zéro et des décimales, le refus d'une paire partielle
sans altération des données existantes, les limites, l'effacement et les droits des comptes réellement connectés.
Les conteneurs et leur stockage temporaires ont été supprimés après la recette.
La présentation des nouveaux messages reste à confirmer visuellement.

Recette manuelle : créer une exploitation sans coordonnées, puis essayer une latitude seule.
Vérifier le refus, ajouter la longitude et enregistrer ; essayer aussi la paire `(0, 0)`.
Ouvrir le détail, puis effacer les deux valeurs et vérifier le message de localisation absente.
Comme agriculteur ou agronome, vérifier la consultation d'une exploitation accessible et le refus du formulaire de modification.

## Troisième livraison — API météo OpenWeather

Livraison du **12 novembre 2026**. L'API `GET /api/parcelles/{id}/meteo` consulte le point de l'exploitation.
Les trois rôles disposent de la consultation selon leurs droits ; les affectations sont vérifiées avant le cache.
Les données sont converties en DTO dédiés : météo actuelle et prévisions à trois heures sur cinq jours au maximum.
Les températures sont en °C, l'humidité et la probabilité en %, la pluie en mm avec sa période explicite.
Les dates restent en UTC et le décalage communiqué par le fournisseur est exposé séparément.

Les réponses distinguent disponibilité, données périmées, panne, coordonnées absentes et coordonnées invalides.
Une donnée absente reste nulle. Les structures, les valeurs et les dates incohérentes sont refusées.
Le cache de 15 minutes est partagé par coordonnées. Après une panne, une nouvelle tentative attend une minute.
Les dernières données connues restent disponibles avec le statut `PERIMEE` pendant deux heures au plus depuis leur récupération.
Le changement de coordonnées utilise un autre cache ; le retrait d'affectation bloque les données déjà en cache.
Les délais de connexion et de lecture sont bornés et la taille du cache est limitée.

La clé réelle reste hors des fichiers du projet et des réponses API.
L'application démarre aussi sans clé et signale alors l'indisponibilité météo.
Les instructions pour charger la variable Windows dans le terminal et redémarrer le backend se trouvent dans [METEO.md](METEO.md).

Vérifications : **139 tests backend habituels réussis**, dont 26 nouveaux tests météo :
12 pour les réponses HTTP et les erreurs, 8 pour le cache et 6 pour l'API et ses droits.
Un contrôle optionnel supplémentaire utilise OpenWeather réel ; il a réussi avec la clé fournie et le point public de Sfax.
Les deux API ont répondu HTTP 200 et 40 échéances ont été reçues lors du contrôle initial.
La recette PostgreSQL/Keycloak compte **26 tests réussis**. Son nouveau parcours utilise un fournisseur météo HTTP local,
de vrais comptes connectés et deux parcelles partageant leurs coordonnées ; il vérifie aussi le refus après retrait d'affectation.
Les conteneurs et leur stockage temporaires ont été supprimés après exécution.
Les tests Angular restent ceux de la livraison précédente : aucun écran n'a changé à cette étape.

## Quatrième livraison — Météo dans le détail d'une parcelle

Livraison du **12 novembre 2026**. Le détail d'une parcelle affiche désormais la météo,
avec un modèle dans `core/models`, un service dans `core/services` et un composant dans `features/meteo`.
Le chargement météo est indépendant des informations de la parcelle et de ses plantations.

La carte présente le point de l'exploitation, les dernières conditions, la date des données,
la température en °C, l'humidité en % et la pluie sur la dernière heure en mm.
Les prévisions à trois heures sont paginées avec le composant partagé, sans nouvel appel fournisseur au changement de page.
Leur humidité, probabilité de précipitations et pluie sur trois heures gardent leurs unités explicites.
Les dates utilisent le décalage UTC communiqué par OpenWeather, indiqué sur la carte.
Les valeurs nulles affichent « Non disponible » ; les zéros restent affichés comme des valeurs.

Les états de panne, données périmées, localisation manquante et localisation invalide sont distincts.
Seul l'administrateur dispose du lien pour compléter les coordonnées de l'exploitation.
Les erreurs réseau et de session sont explicites. Un nouvel appel efface les anciennes données ;
les requêtes sont annulées au changement de parcelle ou à la destruction du composant.
Le logo officiel est stocké localement et l'attribution mène au site OpenWeather.
Aucun bouton Actualiser n'a été ajouté.

Vérifications : **148 tests Angular réussis**, dont **17 nouveaux tests météo**.
Ils couvrent les unités, les zéros, les valeurs nulles, les dates et les décalages UTC,
les états de panne, les trois rôles, la pagination locale, le refus d'accès et la séparation du chargement des plantations.
La compilation de production réussit avec **492,26 kB** de chargement initial, sous le budget de 500 kB.
Rapports : `backend/target/meteo-angular-tests.log` et `backend/target/meteo-angular-build.log`.

Le contrôle du navigateur n'a pas démarré : `helper_unknown_error: setup refresh had errors`.
La tâche de validation visuelle reste ouverte et ne sera cochée qu'après vérification des vues ordinateur et mobile.

Recette manuelle : ouvrir le détail d'une parcelle dont l'exploitation possède des coordonnées.
Vérifier les conditions, les dates, les unités et les pages de prévisions.
Vérifier aussi une exploitation sans coordonnées, le lien de modification réservé à l'administrateur,
la consultation comme agriculteur et agronome, puis l'affichage à une largeur mobile.

La prochaine étape fonctionnelle est **l'étape 5 — Définir et calculer les alertes**.

### Correction du défilement sur ordinateur — 13 novembre 2026

La vidéo fournie montre une coupure commune au fond de la page, à la sidebar et au contenu météo
sur grand écran. Les deux premières modifications n'ont pas résolu le défaut dans la session concernée.
Le layout final retire la hauteur de viewport et le découpage du conteneur global : le document défile
normalement et la sidebar reste en place sur ordinateur avec `position: sticky` et `top: 0`.
Les composants de détail et de météo sont affichés en bloc ; la carte météo ne découpe pas son contenu.

Le défaut initial ne se reproduit pas dans un Firefox isolé, même avec le chargement Angular asynchrone.
Le contrôle final avec des données fictives redimensionne la même page sans rechargement aux largeurs
1000, 1117, 1280, 1920, 1024, 830, 390 puis 1920 px. Il confirme par mesure et capture l'accès aux
conditions, à la dernière prévision, au pied météo et aux plantations, ainsi que le défilement normal
du document et la stabilité de la sidebar sur ordinateur. Ce contrôle ne remplace pas la confirmation
dans la session concernée.
Les 148 tests Angular et la compilation de production réussissent (492,35 kB de chargement initial).
Rapports : `backend/target/meteo-defilement-tests.log`, `backend/target/meteo-defilement-build.log`
et `backend/target/meteo-defilement-firefox.log`.
L'utilisateur a confirmé le bon fonctionnement après cette correction le **13 novembre 2026**.
Les contrôles Firefox aux différentes largeurs et cette confirmation terminent l'étape 4.

## Cinquième livraison — Calcul périodique des alertes

Livraison du **14 novembre 2026**. Les règles et leurs limites sont décrites dans [ALERTES.md](ALERTES.md).
Les prévisions des prochaines 24 heures déclenchent les types chaleur, gel et pluie importante.
Les seuils initiaux d'avertissement sont 35 °C, 0 °C ou moins, et 20 mm sur trois heures.
Les niveaux critiques utilisent 40 °C, −3 °C ou moins, et 40 mm sur trois heures.
Ces paramètres sont configurables et validés au démarrage ; ils restent des règles de suivi à adapter.

Le calcul démarre après une minute, puis se répète toutes les 30 minutes.
La collecte interne partage le cache météo avec l'API sans nécessiter de session utilisateur pour la tâche.
L'API météo conserve ses contrôles d'accès avant le cache, y compris après retrait d'affectation.
La collecte réseau est effectuée hors transaction ; une transaction courte verrouille ensuite la parcelle.
Une contrainte unique protège la combinaison parcelle, type, échéance et coordonnées.

Les nouvelles données mettent à jour l'alerte existante, y compris son niveau.
Les alertes expirent à leur échéance ou lorsque le point météo change ; l'historique est conservé.
Une nouvelle valeur exploitable sous le seuil lève l'alerte ; une panne, une prévision absente ou des données
périmées ne la lèvent pas. Une réponse plus ancienne ne remplace pas une source plus récente.
Le type, la valeur, le seuil, l'unité, le point et les dates sources sont enregistrés sans migration SQL.
L'API de consultation et les écrans restent à réaliser aux étapes 6 et 7.

Vérifications : **162 tests backend habituels réussis**, dont **23 nouveaux contrôles** :
19 pour le calcul et la persistance, 3 pour les paramètres et 1 pour le cache partagé avec l'API.
Ils couvrent notamment les bornes inclusives, la fraîcheur, l'expiration, les coordonnées modifiées,
la concurrence, la contrainte unique, les pannes et l'absence de transaction pendant la collecte.
Rapport : `backend/target/alertes-tests.log`.
La recette avec PostgreSQL et Keycloak temporaires compte **27 tests réussis**, dont un nouveau parcours
de persistance et de réévaluation des alertes. Les conteneurs et leur stockage ont été supprimés après la recette.
Rapport : `backend/target/recette-reelle.log`.

## Sixième livraison — API des alertes et lecture personnelle

Livraison du **14 novembre 2026**. Le contrat des quatre routes est décrit dans [ALERTES.md](ALERTES.md) :
liste paginée, détail, compteur d'alertes actives non lues et marquage personnel comme lu.
Les DTO sont regroupés dans `dto/alerte`, avec une interface `AlerteService` et son implémentation.
La consultation ne déclenche ni appel météo ni génération d'alertes.

Les filtres facultatifs portent sur la parcelle, le type, le niveau, le statut, la lecture et les dates
d'échéance par journées UTC inclusives. Le tri est stable et les filtres s'appliquent avant la pagination.
Les requêtes d'accès utilisent les affectations actuelles sans multiplier les lignes des parcelles partagées.
L'administrateur dispose de la vue globale ; l'agriculteur et l'agronome conservent leur périmètre autorisé.

La lecture est stockée par alerte et identité Keycloak. Le corps d'une requête ne choisit jamais le compte.
Les répétitions et les marquages simultanés conservent une seule lecture et sa première date.
Une lecture ne lève pas l'alerte et n'affecte pas les autres comptes. La réévaluation du même identifiant
conserve la lecture. Le retrait d'affectation bloque immédiatement les détails, lectures, filtres et compteurs concernés.

L'état effectif tient compte des échéances et des changements de coordonnées dès la consultation,
sans attendre la tâche planifiée et sans écriture pendant un GET.
Les données anciennes sont signalées séparément de l'état de risque connu.
La cohérence des coordonnées traite `0.0` et `-0.0` comme le même point.

Vérifications : **184 tests backend habituels réussis**, dont **21 nouveaux tests API** et un contrôle
supplémentaire du calcul aux coordonnées égales à zéro, avec ou sans signe. Ils couvrent les trois rôles, le partage,
les filtres combinés, les bornes UTC, la pagination, l'idempotence, la concurrence, l'expiration immédiate,
la fraîcheur, la désactivation de compte et le retrait d'affectation.
Rapports : `backend/target/alertes-api-tests.log` et `backend/target/alertes-api-complets-tests.log`.
La recette PostgreSQL/Keycloak compte désormais **28 tests réussis**. Le nouveau parcours vérifie
les filtres combinés, la pagination, les lectures séparées entre comptes, la répétition d'un marquage,
les compteurs et le retrait puis rétablissement d'une affectation avec les mêmes JWT.
Les services et le stockage temporaires ont été supprimés après la recette.
Rapport : `backend/target/recette-reelle.log`.

## Septième livraison — Écrans Angular des alertes

Livraison du **14 novembre 2026**. Le menu latéral contient désormais « Alertes » pour les trois rôles.
Le détail d'une parcelle propose aussi un accès à ses alertes. Les composants sont chargés à la demande,
avec modèles et service HTTP dans `core`, écrans dans `features/alerte` et badges partagés dans `shared`.

La liste affiche les alertes actives par défaut. Les filtres couvrent la parcelle, le type, le niveau,
l'état, la lecture personnelle et les dates d'échéance UTC inclusives. La pagination est effectuée côté API.
Le retour depuis le détail conserve les filtres, la page et sa taille, puis recharge les données.
Le compteur porte uniquement sur les alertes actives non lues du périmètre de parcelles affiché ;
il reste indépendant des autres filtres et dispose de son propre état d'erreur.

Le détail présente les seuils, valeurs, unités, coordonnées et dates sources en UTC.
La fraîcheur des données est distincte de l'état de l'alerte. Ouvrir un détail ne marque pas l'alerte comme lue :
l'action explicite enregistre seulement la lecture du compte connecté, sans lever l'alerte.
Les doubles clics sont bloqués pendant l'enregistrement. Une erreur ne produit pas de confirmation fictive.
Un refus d'accès efface les données affichées ; les requêtes précédentes sont annulées lors des changements de contexte.
Aucun bouton Actualiser n'a été ajouté et le défilement naturel existant est conservé.

Vérifications : **173 tests Angular réussis**, dont **25 nouveaux tests** pour les alertes.
Ils couvrent les trois rôles, les gardes, les filtres, les dates invalides, la pagination,
la conservation des filtres, les erreurs indépendantes, la lecture personnelle et les refus d'accès.
La compilation de production réussit avec **494,98 kB** de chargement initial, sous le budget de 500 kB.
Rapports : `backend/target/alertes-angular-complets-tests.log` et `backend/target/alertes-angular-build.log`.

Un Firefox isolé a vérifié la liste pour les trois rôles à **1920, 1280, 1024, 1000 et 390 px**,
ainsi que le détail à 1920 et 390 px. Les captures ont été examinées ; les mesures confirment
l'absence de débordement horizontal et l'accès aux dernières cartes. Les champs ont une hauteur de 48 px.
Le parcours filtre → détail → lecture explicite → retour conserve le filtre et recharge l'état de lecture.
Cette vérification utilise une API et une authentification fictives dans un serveur de diagnostic isolé ;
elle ne remplace pas la recette finale avec les comptes réels de l'étape 9.
Rapport : `backend/target/alertes-ui-firefox.log` ; captures `backend/target/alertes-liste-*.png`
et `backend/target/alertes-detail-*.png`. Les processus créés pour ce contrôle ont été arrêtés.

Recette manuelle : ouvrir « Alertes », sélectionner une parcelle et appliquer un type ou une période.
Consulter le détail, vérifier les unités et les dates UTC, puis marquer l'alerte comme lue.
Revenir à la liste pour constater la lecture et le compteur mis à jour. Un autre compte doit conserver
sa propre lecture. Les alertes disponibles dépendent des prévisions et des seuils ; une liste vide est possible.

La prochaine étape est **l'étape 8 — Enrichir les tableaux de bord**.

## Huitième livraison — Tableaux de bord agricoles

Livraison du **14 novembre 2026**. L'accueil conserve ses raccourcis par rôle et ajoute un bilan agricole.
Le contrat, le périmètre de chaque indicateur et la recette manuelle sont décrits dans
[TABLEAU_DE_BORD.md](TABLEAU_DE_BORD.md).
Les DTO sont regroupés dans `dto/tableaudebord`, avec interface et implémentation de service.
Le module utilise les tables existantes ; aucune migration ni nouvelle table n'est nécessaire.

Trois lectures indépendantes exposent la synthèse, les cinq activités récentes et les alertes actives/non lues.
Les filtres communs portent sur la période, la campagne et la parcelle. Les trente derniers jours UTC
sont proposés par défaut. Les opérations utilisent leurs dates civiles inclusives ; les alertes utilisent
leurs journées d'échéance UTC. Les champs et les légendes précisent ces périmètres.
Les compteurs d'organisation existants restent indépendants des filtres du bilan.

Les agrégations sont effectuées dans le backend sur les affectations actuelles, sans multiplier les
parcelles partagées ni les campagnes associées à plusieurs parcelles. Les fiches de plantations pérennes
restent comptées dans leur campagne même si la date de plantation est antérieure.
Les volumes sont additionnés en m³ et les récoltes en kg à partir des valeurs normalisées existantes.
Les activités de tables différentes conservent leur identité et le tri est stable.
Les alertes utilisent l'état actif effectif et la lecture personnelle du compte connecté.

Angular distingue les filtres modifiés des filtres appliqués. Les erreurs de synthèse, activités et alertes
restent indépendantes, sans inventer de zéro en cas de panne. Les listes de choix peuvent aussi signaler
leur indisponibilité séparément. Un refus d'accès efface le bilan ; les appels précédents sont annulés
au changement de filtre et à la destruction du composant. Aucun bouton Actualiser n'a été ajouté.
Les dates civiles ont été vérifiées sans conversion de fuseau qui pourrait les décaler d'un jour.
L'accueil est chargé à la demande pour respecter le budget initial de compilation.

Vérifications : **200 tests backend habituels réussis**, dont **16 nouveaux tests du tableau de bord**.
Ils couvrent les trois rôles, les parcelles partagées, les conversions, les bornes inclusives, les filtres,
les campagnes sans plantation, les cultures pérennes, le tri des activités, les lectures personnelles,
l'expiration, les coordonnées modifiées, la désactivation de compte et le retrait d'affectation.
Rapports : `backend/target/tableau-de-bord-tests.log` et `backend/target/tableau-de-bord-complets-tests.log`.

La recette PostgreSQL/Keycloak compte **29 tests réussis**, dont un nouveau parcours avec deux agriculteurs
et un agronome, des activités créées par l'API, les unités mélangées, les filtres, les lectures d'alertes
et le retrait individuel d'affectation avec le même JWT. Les conteneurs et leur stockage temporaires
ont été supprimés après exécution. Rapport : `backend/target/recette-reelle.log`.

Les **189 tests Angular réussissent**, dont **16 nouveaux tests** du bilan. La compilation de production
réussit avec **485,08 kB** de chargement initial, sous le budget de 500 kB.
Rapports : `backend/target/tableau-de-bord-angular-complets-tests.log`
et `backend/target/tableau-de-bord-angular-build.log`.
Un Firefox isolé a vérifié les trois rôles aux largeurs **1920, 1280, 1024, 1000 et 390 px**.
Les champs mesurent 48 px de haut, les cumuls et la dernière activité restent accessibles et aucun
débordement horizontal n'a été relevé. Les captures ont été examinées sur ordinateur et mobile.
La soumission des filtres et une panne isolée du bloc alertes ont aussi été vérifiées.
Cette recette visuelle utilise une API et une authentification fictives ; les accès réels sont testés
séparément avec PostgreSQL/Keycloak. Rapport : `backend/target/tableau-ui-firefox.log` et captures
`backend/target/tableau-bilan-*.png`, `backend/target/tableau-activites-*.png`.
Les processus créés pour le contrôle Firefox ont été arrêtés.

La prochaine étape est **l'étape 9 — Recette et livraison du sprint 3**.

## Neuvième livraison — Recette finale et clôture

Clôture technique du **14 novembre 2026**, avec **30 tâches terminées sur 30**.
Le guide [RECETTE_SPRINT_3.md](RECETTE_SPRINT_3.md) consigne les résultats, les commandes reproductibles,
le parcours fonctionnel et les limites des vérifications.

Le nouveau test de recette PostgreSQL/Keycloak relie la collecte HTTP météo, la génération d'alertes
sans doublon, la lecture personnelle, les compteurs du tableau de bord et le retrait individuel d'affectation.
Une horloge météo de test permet aussi de provoquer une source périmée, de dépasser le délai de secours
et d'atteindre exactement l'échéance, sans attendre plusieurs heures. Le fournisseur est un serveur HTTP
local ; les comptes, JWT, autorisations et données PostgreSQL restent réels.
Une panne ne lève pas les alertes connues ; le retrait d'accès est vérifié avec le même JWT.
Les services et le stockage temporaires ont été supprimés en fin de recette.

Résultats finaux : **200 tests backend habituels réussis et JAR créé**, **189 tests Angular réussis**,
**30 tests PostgreSQL/Keycloak réussis**. La compilation Angular de production réussit avec
**485,08 kB** de chargement initial, sous le budget de 500 kB.
Le test OpenWeather réel est facultatif et n'a pas été relancé pendant cette clôture.

La commande `npm run recette:sprint3:ui`, depuis `frontend`, exécute un Firefox isolé avec API et
authentification fictives. Elle vérifie le parcours parcelle/météo → alertes → détail → lecture → accueil,
les trois rôles et six largeurs : 320, 390, 768, 1024, 1280 et 1920 px.
Les **72 contrôles de vue** réussissent, sans débordement horizontal ni contenu masqué.
Les captures sur ordinateur/mobile ont été examinées. La fermeture du menu mobile après navigation,
la fraîcheur affichée, l'indisponibilité météo et l'effacement des données après refus sont également vérifiés.
Cette validation visuelle ne prétend pas être une connexion navigateur à Keycloak/PostgreSQL réels
ni une confirmation personnelle de l'utilisateur pour les nouveaux écrans.

Rapports : `backend/target/sprint3-livraison-backend.log`,
`backend/target/sprint3-livraison-angular-tests.log`, `backend/target/sprint3-livraison-angular-build.log`,
`backend/target/recette-reelle.log` et `backend/target/sprint3-livraison-ui.log`.
Les captures et le détail des contrôles de vue sont dans `backend/target/recette-sprint3-ui/`.
Les processus créés pour la recette Firefox ont été arrêtés.

La prochaine étape est **le sprint 4 — Machine Learning et DevOps**.
