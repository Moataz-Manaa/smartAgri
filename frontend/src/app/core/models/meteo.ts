export type StatutMeteo =
  'DISPONIBLE' | 'PERIMEE' | 'INDISPONIBLE' | 'LOCALISATION_MANQUANTE' | 'LOCALISATION_INVALIDE';

export interface ConditionsMeteoResponse {
  dateDonnees: string;
  temperatureCelsius: number | null;
  humiditePourcent: number | null;
  pluieDerniereHeureMillimetres: number | null;
  description: string | null;
}

export interface PrevisionMeteoResponse {
  datePrevision: string;
  temperatureCelsius: number | null;
  humiditePourcent: number | null;
  probabilitePrecipitationsPourcent: number | null;
  pluieTroisHeuresMillimetres: number | null;
  description: string | null;
}

export interface DonneesMeteoResponse {
  recupereesLe: string;
  decalageUtcSecondes: number;
  conditions: ConditionsMeteoResponse;
  previsions: PrevisionMeteoResponse[];
}

export interface MeteoResponse {
  parcelleId: number;
  exploitationId: number;
  exploitationNom: string;
  latitude: number | null;
  longitude: number | null;
  statut: StatutMeteo;
  message: string;
  source: string;
  lienSource: string;
  donnees: DonneesMeteoResponse | null;
}
