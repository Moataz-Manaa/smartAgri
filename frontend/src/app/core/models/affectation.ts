import { RoleUtilisateur } from './utilisateur';

export interface AffectationRequest {
  utilisateurId: number;
  parcelleId: number;
}

export interface AffectationResponse {
  id: number;
  utilisateurId: number;
  utilisateurNom: string;
  utilisateurPrenom: string;
  utilisateurEmail: string;
  role: RoleUtilisateur | null;
  parcelleId: number;
  parcelleCode: string;
  parcelleNom: string;
  exploitationNom: string;
}
