export type StatutCampagne = 'PLANIFIEE' | 'EN_COURS' | 'CLOTUREE';

export interface CampagneRequest {
  nom: string;
  description: string | null;
  dateDebut: string;
  dateFin: string;
  parcelleIds: number[];
}

export interface ParcelleCampagne {
  id: number;
  code: string;
  nom: string;
  exploitationId: number;
  exploitationNom: string;
}

export interface CampagneResponse extends CampagneRequest {
  id: number;
  statut: StatutCampagne;
  parcelles: ParcelleCampagne[];
}
