export interface CultureRequest {
  nom: string;
  description: string | null;
  type: string | null;
}

export interface CultureResponse extends CultureRequest {
  id: number;
}
