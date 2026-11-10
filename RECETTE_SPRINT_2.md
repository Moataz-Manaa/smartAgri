# Recette du sprint 2 — SmartAgri

Recette automatisée : **9 novembre 2026**. Clôture : **10 novembre 2026**.

**Sprint 2 terminé — 36/36 tâches, soit 100 %.**

Le sprint couvre le catalogue, les campagnes, les plantations, les observations,
l'irrigation, la fertilisation, les traitements, les récoltes et leur historique commun.
Les diagnostics ML, les prédictions et la météo appartiennent aux étapes suivantes du projet.

## État de la recette

| Vérification | Résultat | Preuve |
| --- | --- | --- |
| Tests backend et création du JAR | 107 tests réussis | `backend/target/sprint2-livraison-backend.log` |
| Tests Angular après les corrections visuelles | 121 tests réussis le 10 novembre | `backend/target/sidenav-fixe-tests.log` |
| Compilation Angular de production | Réussie le 10 novembre | `backend/target/sidenav-fixe-build.log` |
| Recette avec PostgreSQL et Keycloak réels | 24 tests réussis | `backend/target/recette-reelle.log` |
| Écrans sur ordinateur | Vérifiés par l'utilisateur le 9 novembre 2026 | Réponse : « Vérifié sur ordinateur uniquement » |
| Écrans sur mobile | Validés globalement par l'utilisateur le 10 novembre 2026 | Réponse : « la vue mobile est correcte » |

La dernière compilation Angular respecte le budget existant de 500 kB : chargement initial de **498,97 kB**.
Les tests backend habituels utilisent H2 et un fournisseur de comptes simulé ;
les tests de recette optionnels utilisent de vrais JWT, PostgreSQL et Keycloak dans des conteneurs jetables.
Ils sont ignorés dans la commande Maven habituelle et exécutés séparément par le script de recette.
Les rapports et les compilations sont générés localement dans des dossiers ignorés par Git.

L'agent n'a pas observé les écrans : le démarrage de l'outil Windows a échoué avec
`helper_unknown_error: setup refresh had errors`. Les validations sur ordinateur et mobile sont celles de l'utilisateur.
La confirmation mobile est globale ; les dimensions et les résultats détaillés par écran n'ont pas été consignés.

## Parcours fonctionnel de référence

Ces données sont fictives. Les noms peuvent être adaptés pour éviter les doublons dans le catalogue.

1. **Administrateur** : créer une culture « Olivier », de type « Arboriculture », et sa variété « Chemlali ».
2. Créer une exploitation et une parcelle de 5 ha ; affecter deux agriculteurs et un agronome à cette parcelle.
3. Créer une campagne du 1er janvier au 31 décembre 2026, la rattacher à cette parcelle et la passer en cours.
4. **Premier agriculteur** : créer une fiche de plantation de 2 ha pour cette campagne et cette variété.
   Pour les oliviers existants, la date de plantation physique peut précéder la campagne.
5. Enregistrer une observation le 1er novembre, puis une irrigation de 1 000 L le 2 novembre.
6. Enregistrer une fertilisation et un traitement avec des noms de produits et des quantités fictifs ;
   vérifier les dates, unités et informations obligatoires. Ces saisies ne constituent pas des conseils agronomiques.
7. **Second agriculteur** : enregistrer une irrigation de 2 m³. Vérifier le cumul de **3 m³**.
8. Enregistrer deux récoltes : **500 kg**, puis **2 tonnes**, à des dates passées de la campagne.
   Vérifier le total de **2 500 kg** et le rendement réel de **1 250 kg/ha** pour les 2 ha.
9. **Agronome** : consulter les activités, les bilans et la chronologie sans bouton de saisie ou de modification.
10. Dans l'historique, filtrer par type et dates, changer la taille des pages, puis réinitialiser les filtres.
    Vérifier les dates limites incluses, un résultat vide et le refus d'une période inversée.
11. **Administrateur** : retirer uniquement l'affectation du premier agriculteur.
    Sa consultation et ses écritures doivent être refusées, y compris par URL directe ou appel API.
    Le second agriculteur et l'agronome conservent leur accès ; aucune opération n'est supprimée.
12. Réaffecter le premier agriculteur : l'historique doit redevenir accessible.
13. Clôturer la campagne. Les activités et les bilans restent lisibles ; aucune nouvelle opération ni correction n'est acceptée.

L'interface peut encore montrer des données déjà chargées au moment du retrait d'affectation.
Le backend doit refuser chaque nouvel appel ; un nouveau chargement de l'historique efface les données et affiche le refus.

## Contrôles d'autorisation

| Action | Administrateur | Agriculteur affecté | Agronome affecté | Agriculteur/agronome non affecté |
| --- | --- | --- | --- | --- |
| Créer ou modifier le catalogue | Autorisé | Refusé | Refusé | Refusé |
| Créer ou modifier une campagne | Autorisé | Refusé | Refusé | Refusé |
| Consulter la campagne, la plantation et ses activités | Autorisé | Autorisé | Autorisé | Refusé |
| Créer ou modifier une plantation et ses activités | Refusé | Autorisé si campagne ouverte | Refusé | Refusé |
| Consulter les cumuls et l'historique | Autorisé | Autorisé | Autorisé | Refusé |
| Modifier une activité après clôture | Refusé | Refusé | Refusé | Refusé |

Le catalogue est commun et consultable par les trois rôles, indépendamment des affectations.
L'administrateur dispose d'une consultation globale sans affectation à une parcelle.
Les affectations sont contrôlées à chaque appel backend ; cacher les liens dans Angular ne suffit pas.

## Guide de vérification mobile — validation globale reçue

Utiliser un téléphone ou le mode adaptatif du navigateur, par exemple **390 × 844** puis **768 × 1024**.
Tester aussi une largeur de 320 px pour détecter les débordements.
Les services locaux doivent rester accessibles depuis le navigateur utilisé.

- **Catalogue** : listes, filtre des variétés, formulaires et messages d'erreur lisibles ; actions conformes aux trois rôles.
- **Campagnes** : liste, détail, choix des parcelles, dates et changement de statut utilisables.
- **Plantations** : liste, détail, formulaire et liens vers la parcelle et la campagne accessibles.
- **Opérations** : les cinq formulaires restent lisibles ; les unités, erreurs et boutons ne sont pas coupés.
- **Bilans** : total d'irrigation, quantité récoltée et rendement correctement affichés.
- **Historique** : filtres lisibles, dates saisissables, cartes et pagination utilisables ; listes détaillées faciles à ouvrir.
- **Navigation** : menu mobile ouvrable et refermable ; déconnexion accessible ; aucun bouton Actualiser.
- **Lisibilité** : aucun chevauchement ni défilement horizontal de la page ; labels et focus clavier visibles.

Pour signaler une anomalie, noter la page, le rôle, la largeur et les étapes qui permettent de la reproduire.
L'utilisateur a confirmé le bon affichage mobile le 10 novembre 2026 ; le sprint est clôturé.
Les points ci-dessus restent un guide pour les prochaines vérifications visuelles.

## Reproduire les contrôles automatisés

Depuis `backend`, avec Maven et Java 21 :

```powershell
mvn package
```

Depuis `frontend` :

```powershell
npm test -- --watch=false
npm run build
```

Depuis la racine, avec Docker Desktop démarré, Python et Maven :

```powershell
python keycloak/verify_backend.py
```

La recette crée ses propres services, données et secrets temporaires.
Elle ne réutilise pas la base métier ni le conteneur Keycloak habituel.
Elle supprime ses conteneurs et leur stockage en fin d'exécution.
Le script suppose que les images et les dépendances Maven nécessaires sont disponibles ;
les détails figurent dans [la documentation Keycloak](keycloak/README.md).

Les commandes exécutées pour cette livraison utilisent le cache Maven local déjà disponible :

```powershell
mvn -o "-Dmaven.repo.local=C:/Users/manaa/.m2/repository" -B package
```

Lancement habituel de l'application : voir [le README](README.md).
