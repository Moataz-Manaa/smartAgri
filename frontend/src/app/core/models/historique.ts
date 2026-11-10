export type TypeActivite =
  'OBSERVATION' | 'IRRIGATION' | 'FERTILISATION' | 'TRAITEMENT' | 'RECOLTE';

export interface ActiviteResponse {
  id: number;
  type: TypeActivite;
  date: string;
  description: string | null;
  nomProduit: string | null;
  quantite: number | null;
  unite: string | null;
  auteurNom: string;
  auteurPrenom: string;
}
