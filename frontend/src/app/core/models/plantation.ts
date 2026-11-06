export type StatutPlantation = 'PLANIFIEE' | 'EN_COURS' | 'TERMINEE';

export interface PlantationRequest {
  parcelleId: number;
  campagneId: number;
  varieteId: number;
  datePlantation: string;
  superficie: number;
  quantitePlantee: number | null;
  dateRecoltePrevue: string | null;
  commentaire: string | null;
  statut: StatutPlantation;
}

export interface PlantationResponse extends PlantationRequest {
  id: number;
  parcelleCode: string;
  parcelleNom: string;
  campagneNom: string;
  campagneCloturee: boolean;
  varieteNom: string;
  cultureId: number;
  cultureNom: string;
}
