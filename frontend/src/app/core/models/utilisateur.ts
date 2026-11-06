export type RoleUtilisateur = 'ADMIN' | 'AGRICULTEUR' | 'AGRONOME';

export interface UtilisateurModificationRequest {
  nom: string;
  prenom: string;
  email: string;
  role: RoleUtilisateur;
}

export interface UtilisateurCreationRequest extends UtilisateurModificationRequest {
  username: string;
  motDePasse: string;
}

export interface UtilisateurResponse {
  id: number;
  keycloakId: string;
  username: string;
  nom: string;
  prenom: string;
  email: string;
  role: RoleUtilisateur | null;
  actif: boolean;
}
