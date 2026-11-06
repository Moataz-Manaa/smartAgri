export interface ParcelleRequest {
  nom: string;
  superficie: number;
  typeSol: string | null;
  typeIrrigation: string | null;
  description: string | null;
  exploitationId: number;
}

export interface ParcelleResponse extends ParcelleRequest {
  id: number;
  code: string;
  exploitationCode: string;
  exploitationNom: string;
}
