import { TypeActivite } from './historique';

export interface TableauDeBordFiltres {
  parcelleId: number | null;
  campagneId: number | null;
  dateDebut: string;
  dateFin: string;
}

export interface SyntheseResponse {
  dateDebut: string;
  dateFin: string;
  nombreParcelles: number;
  nombreCampagnes: number;
  nombrePlantations: number;
  nombreActivites: number;
  volumeIrrigationMetresCubes: number;
  quantiteRecolteeKilogrammes: number;
}

export interface ActiviteRecenteResponse {
  id: number;
  type: TypeActivite;
  date: string;
  plantationId: number;
  parcelleId: number;
  parcelleCode: string;
  parcelleNom: string;
  campagneNom: string;
  description: string | null;
}

export interface AlertesTableauDeBordResponse {
  actives: number;
  activesNonLues: number;
  calculeLe: string;
}
