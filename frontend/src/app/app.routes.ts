import { Routes } from '@angular/router';

import { adminGuard } from './core/guards/admin-guard';
import { agriculteurGuard } from './core/guards/agriculteur-guard';
import { lectureGuard } from './core/guards/lecture-guard';
import { Accueil } from './features/accueil/accueil';
import { CultureListe } from './features/culture/culture-liste/culture-liste';
import { CultureFormulaire } from './features/culture/culture-formulaire/culture-formulaire';
import { VarieteListe } from './features/variete/variete-liste/variete-liste';
import { VarieteFormulaire } from './features/variete/variete-formulaire/variete-formulaire';
import { ExploitationDetail } from './features/exploitation/exploitation-detail/exploitation-detail';
import { AffectationGestion } from './features/affectation/affectation-gestion/affectation-gestion';
import { AccesRefuse } from './features/acces-refuse/acces-refuse';
import { ExploitationListe } from './features/exploitation/exploitation-liste/exploitation-liste';
import { ExploitationFormulaire } from './features/exploitation/exploitation-formulaire/exploitation-formulaire';
import { ParcelleListe } from './features/parcelle/parcelle-liste/parcelle-liste';
import { ParcelleFormulaire } from './features/parcelle/parcelle-formulaire/parcelle-formulaire';
import { UtilisateurListe } from './features/utilisateur/utilisateur-liste/utilisateur-liste';
import { UtilisateurFormulaire } from './features/utilisateur/utilisateur-formulaire/utilisateur-formulaire';

export const routes: Routes = [
  {
    path: 'fertilisations/nouvelle',
    loadComponent: chargerFormulaireFertilisation,
    canActivate: [agriculteurGuard],
  },
  {
    path: 'fertilisations/:id/modifier',
    loadComponent: chargerFormulaireFertilisation,
    canActivate: [agriculteurGuard],
  },
  {
    path: 'traitements/nouvelle',
    loadComponent: chargerFormulaireTraitement,
    canActivate: [agriculteurGuard],
  },
  {
    path: 'traitements/:id/modifier',
    loadComponent: chargerFormulaireTraitement,
    canActivate: [agriculteurGuard],
  },
  {
    path: 'irrigations/nouvelle',
    loadComponent: chargerFormulaireIrrigation,
    canActivate: [agriculteurGuard],
  },
  {
    path: 'irrigations/:id/modifier',
    loadComponent: chargerFormulaireIrrigation,
    canActivate: [agriculteurGuard],
  },
  {
    path: 'observations/nouvelle',
    loadComponent: chargerFormulaireObservation,
    canActivate: [agriculteurGuard],
  },
  {
    path: 'observations/:id/modifier',
    loadComponent: chargerFormulaireObservation,
    canActivate: [agriculteurGuard],
  },
  { path: 'plantations', loadComponent: chargerListePlantations, canActivate: [lectureGuard] },
  {
    path: 'plantations/nouvelle',
    loadComponent: chargerFormulairePlantation,
    canActivate: [agriculteurGuard],
  },
  {
    path: 'plantations/:id/modifier',
    loadComponent: chargerFormulairePlantation,
    canActivate: [agriculteurGuard],
  },
  { path: 'plantations/:id', loadComponent: chargerDetailPlantation, canActivate: [lectureGuard] },
  { path: 'campagnes', loadComponent: chargerListeCampagnes, canActivate: [lectureGuard] },
  {
    path: 'campagnes/nouvelle',
    loadComponent: chargerFormulaireCampagne,
    canActivate: [adminGuard],
  },
  {
    path: 'campagnes/:id/modifier',
    loadComponent: chargerFormulaireCampagne,
    canActivate: [adminGuard],
  },
  { path: 'campagnes/:id', loadComponent: chargerDetailCampagne, canActivate: [lectureGuard] },
  { path: 'cultures', component: CultureListe, canActivate: [lectureGuard] },
  { path: 'cultures/nouvelle', component: CultureFormulaire, canActivate: [adminGuard] },
  { path: 'cultures/:id/modifier', component: CultureFormulaire, canActivate: [adminGuard] },
  { path: 'varietes', component: VarieteListe, canActivate: [lectureGuard] },
  { path: 'varietes/nouvelle', component: VarieteFormulaire, canActivate: [adminGuard] },
  { path: 'varietes/:id/modifier', component: VarieteFormulaire, canActivate: [adminGuard] },
  {
    path: '',
    redirectTo: 'accueil',
    pathMatch: 'full',
  },
  {
    path: 'accueil',
    component: Accueil,
    canActivate: [lectureGuard],
  },
  {
    path: 'exploitations',
    component: ExploitationListe,
    canActivate: [lectureGuard],
  },
  {
    path: 'exploitations/nouvelle',
    component: ExploitationFormulaire,
    canActivate: [adminGuard],
  },
  {
    path: 'exploitations/:id/modifier',
    component: ExploitationFormulaire,
    canActivate: [adminGuard],
  },
  {
    path: 'parcelles',
    component: ParcelleListe,
    canActivate: [lectureGuard],
  },
  {
    path: 'parcelles/nouvelle',
    component: ParcelleFormulaire,
    canActivate: [adminGuard],
  },
  {
    path: 'parcelles/:id/modifier',
    component: ParcelleFormulaire,
    canActivate: [adminGuard],
  },
  {
    path: 'utilisateurs',
    component: UtilisateurListe,
    canActivate: [adminGuard],
  },
  {
    path: 'utilisateurs/nouveau',
    component: UtilisateurFormulaire,
    canActivate: [adminGuard],
  },
  {
    path: 'utilisateurs/:id/modifier',
    component: UtilisateurFormulaire,
    canActivate: [adminGuard],
  },
  {
    path: 'acces-refuse',
    component: AccesRefuse,
  },
  {
    path: 'affectations',
    component: AffectationGestion,
    canActivate: [adminGuard],
  },
  { path: 'parcelles/:id', loadComponent: chargerDetailParcelle, canActivate: [lectureGuard] },
  { path: 'exploitations/:id', component: ExploitationDetail, canActivate: [lectureGuard] },
];

// Ces écrans sont chargés à leur ouverture pour garder un démarrage léger.
async function chargerListeCampagnes() {
  const composant = await import('./features/campagne/campagne-liste/campagne-liste');
  return composant.CampagneListe;
}

async function chargerFormulaireCampagne() {
  const composant = await import('./features/campagne/campagne-formulaire/campagne-formulaire');
  return composant.CampagneFormulaire;
}

async function chargerDetailCampagne() {
  const composant = await import('./features/campagne/campagne-detail/campagne-detail');
  return composant.CampagneDetail;
}

async function chargerListePlantations() {
  const composant = await import('./features/plantation/plantation-liste/plantation-liste');
  return composant.PlantationListe;
}

async function chargerFormulairePlantation() {
  const composant =
    await import('./features/plantation/plantation-formulaire/plantation-formulaire');
  return composant.PlantationFormulaire;
}

async function chargerDetailPlantation() {
  const composant = await import('./features/plantation/plantation-detail/plantation-detail');
  return composant.PlantationDetail;
}

async function chargerDetailParcelle() {
  const composant = await import('./features/parcelle/parcelle-detail/parcelle-detail');
  return composant.ParcelleDetail;
}

async function chargerFormulaireObservation() {
  const composant =
    await import('./features/observation/observation-formulaire/observation-formulaire');
  return composant.ObservationFormulaire;
}

async function chargerFormulaireIrrigation() {
  const composant =
    await import('./features/irrigation/irrigation-formulaire/irrigation-formulaire');
  return composant.IrrigationFormulaire;
}

async function chargerFormulaireFertilisation() {
  const composant =
    await import('./features/fertilisation/fertilisation-formulaire/fertilisation-formulaire');
  return composant.FertilisationFormulaire;
}

async function chargerFormulaireTraitement() {
  const composant =
    await import('./features/traitement/traitement-formulaire/traitement-formulaire');
  return composant.TraitementFormulaire;
}
