export type UniteRecolte = 'KILOGRAMME' | 'TONNE';

export interface RecolteRequest {
  plantationId: number;
  dateRecolte: string;
  quantite: number;
  unite: UniteRecolte;
  commentaire: string | null;
}

export interface RecolteResponse extends RecolteRequest {
  id: number;
  quantiteKilogrammes: number;
  auteurNom: string;
  auteurPrenom: string;
  campagneCloturee: boolean;
}

export interface TotalRecolteResponse {
  quantiteTotaleKilogrammes: number;
  superficieHectares: number | null;
  rendementKgParHectare: number | null;
}
