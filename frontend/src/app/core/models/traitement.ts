import { UniteQuantite } from './unite-quantite';

export interface TraitementRequest {
  plantationId: number;
  dateTraitement: string;
  nomProduit: string;
  quantite: number;
  unite: UniteQuantite;
  methodeApplication: string | null;
  commentaire: string | null;
  cible: string;
}

export interface TraitementResponse extends TraitementRequest {
  id: number;
  auteurNom: string;
  auteurPrenom: string;
  campagneCloturee: boolean;
}
