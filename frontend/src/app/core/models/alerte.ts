export type TypeAlerte = 'CHALEUR' | 'GEL' | 'PLUIE_IMPORTANTE';
export type NiveauAlerte = 'AVERTISSEMENT' | 'CRITIQUE';
export type StatutAlerte = 'ACTIVE' | 'LEVEE' | 'EXPIREE';

export interface AlerteResponse {
  id: number;
  parcelleId: number;
  parcelleCode: string;
  parcelleNom: string;
  exploitationId: number;
  exploitationNom: string;
  type: TypeAlerte;
  niveau: NiveauAlerte;
  statut: StatutAlerte;
  message: string;
  valeur: number;
  seuil: number;
  unite: string;
  latitude: number;
  longitude: number;
  source: string;
  datePrevision: string;
  debutPeriode: string;
  dateDetection: string;
  derniereVerification: string;
  dateRecuperationMeteo: string;
  dateCloture: string | null;
  motifCloture: string | null;
  donneesRecentes: boolean;
  lue: boolean;
  dateLecture: string | null;
}

export interface AlerteFiltres {
  parcelleId: number | null;
  type: TypeAlerte | '';
  niveau: NiveauAlerte | '';
  statut: StatutAlerte | '';
  lue: boolean | null;
  dateDebut: string;
  dateFin: string;
}

export interface CompteurAlertesResponse {
  activesNonLues: number;
  calculeLe: string;
}
