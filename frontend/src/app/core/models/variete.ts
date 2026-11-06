export interface VarieteRequest {
  nom: string;
  description: string | null;
  cultureId: number;
}

export interface VarieteResponse extends VarieteRequest {
  id: number;
  cultureNom: string;
}
