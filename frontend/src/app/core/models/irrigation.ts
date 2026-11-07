export type UniteVolume = 'LITRE' | 'METRE_CUBE';

export interface IrrigationRequest {
  plantationId: number;
  dateIrrigation: string;
  volume: number;
  unite: UniteVolume;
  commentaire: string | null;
}

export interface IrrigationResponse extends IrrigationRequest {
  id: number;
  volumeMetresCubes: number;
  auteurNom: string;
  auteurPrenom: string;
  campagneCloturee: boolean;
}

export interface TotalIrrigationResponse {
  volumeTotalMetresCubes: number;
}
