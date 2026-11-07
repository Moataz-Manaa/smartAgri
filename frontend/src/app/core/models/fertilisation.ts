import { UniteQuantite } from './unite-quantite';

export interface FertilisationRequest {
  plantationId: number;
  dateFertilisation: string;
  nomProduit: string;
  quantite: number;
  unite: UniteQuantite;
  methodeApplication: string | null;
  commentaire: string | null;
  typeEngrais: string | null;
}

export interface FertilisationResponse extends FertilisationRequest {
  id: number;
  auteurNom: string;
  auteurPrenom: string;
  campagneCloturee: boolean;
}
