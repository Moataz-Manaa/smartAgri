import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/services/auth';
import { ExploitationService } from '../../core/services/exploitation';
import { ParcelleService } from '../../core/services/parcelle';
import { UtilisateurService } from '../../core/services/utilisateur';
import { AffectationService } from '../../core/services/affectation';

@Component({
  selector: 'app-accueil',
  imports: [RouterLink],
  templateUrl: './accueil.html',
  styleUrl: './accueil.css',
})
export class Accueil implements OnInit {
  authService = inject(AuthService);
  private exploitationService = inject(ExploitationService);
  private parcelleService = inject(ParcelleService);
  private utilisateurService = inject(UtilisateurService);
  private affectationService = inject(AffectationService);

  nombreExploitations = signal<number | null>(null);
  nombreParcelles = signal<number | null>(null);
  nombreUtilisateurs = signal<number | null>(null);
  nombreAffectations = signal<number | null>(null);
  chargement = signal(false);
  erreur = signal('');

  ngOnInit(): void {
    this.chargerIndicateurs();
  }

  estAdministrateur(): boolean {
    return this.authService.possedeRole('ADMIN');
  }

  estAgronome(): boolean {
    return this.authService.possedeRole('AGRONOME');
  }

  obtenirRole(): string {
    if (this.estAdministrateur()) return 'Administrateur';
    if (this.estAgronome()) return 'Ingénieur agronome';
    return 'Agriculteur';
  }

  chargerIndicateurs(): void {
    if (this.chargement()) return;
    this.chargement.set(true);
    this.erreur.set('');
    this.nombreExploitations.set(null);
    this.nombreParcelles.set(null);
    this.nombreUtilisateurs.set(null);
    this.nombreAffectations.set(null);

    // Une seule ligne par requête : nous utilisons le total calculé par l'API.
    if (this.estAdministrateur()) {
      forkJoin({
        exploitations: this.exploitationService.consulterPage(0, 1),
        parcelles: this.parcelleService.consulterPage(0, 1),
        utilisateurs: this.utilisateurService.consulterPage(0, 1),
        affectations: this.affectationService.consulterPage(0, 1),
      }).subscribe({
        next: (donnees) => {
          this.nombreExploitations.set(donnees.exploitations.totalElements);
          this.nombreParcelles.set(donnees.parcelles.totalElements);
          this.nombreUtilisateurs.set(donnees.utilisateurs.totalElements);
          this.nombreAffectations.set(donnees.affectations.totalElements);
          this.chargement.set(false);
        },
        error: (erreur: unknown) => this.afficherErreur(erreur),
      });
    } else {
      // Le backend filtre ces deux totaux selon les affectations de la personne.
      forkJoin({
        exploitations: this.exploitationService.consulterPage(0, 1),
        parcelles: this.parcelleService.consulterPage(0, 1),
      }).subscribe({
        next: (donnees) => {
          this.nombreExploitations.set(donnees.exploitations.totalElements);
          this.nombreParcelles.set(donnees.parcelles.totalElements);
          this.chargement.set(false);
        },
        error: (erreur: unknown) => this.afficherErreur(erreur),
      });
    }
  }

  private afficherErreur(erreur: unknown): void {
    this.chargement.set(false);
    if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
      this.erreur.set(
        'Votre session est invalide ou votre compte est désactivé. Connectez-vous à nouveau.',
      );
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
      this.erreur.set('Vos droits ont changé. Reconnectez-vous pour actualiser votre espace.');
    } else {
      this.erreur.set(
        'Impossible de charger les indicateurs. Vérifiez votre connexion, puis réessayez.',
      );
    }
  }
}
