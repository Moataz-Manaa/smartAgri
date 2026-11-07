export interface ObservationRequest {
  plantationId: number;
  dateObservation: string;
  description: string;
}

export interface ObservationResponse extends ObservationRequest {
  id: number;
  auteurNom: string;
  auteurPrenom: string;
  campagneCloturee: boolean;
}
