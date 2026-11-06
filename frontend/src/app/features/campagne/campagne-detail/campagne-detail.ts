import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CampagneResponse, StatutCampagne } from '../../../core/models/campagne';
import { CampagneService } from '../../../core/services/campagne';
import { AuthService } from '../../../core/services/auth';
import { StatutCampagnePipe } from '../../../shared/pipes/statut-campagne';

@Component({
  selector: 'app-campagne-detail',
  imports: [DatePipe, RouterLink, StatutCampagnePipe],
  templateUrl: './campagne-detail.html',
})
export class CampagneDetail implements OnInit {
  authService = inject(AuthService);
  private service = inject(CampagneService);
  private route = inject(ActivatedRoute);
  campagne = signal<CampagneResponse | null>(null);
  chargement = signal(false);
  enregistrement = signal(false);
  confirmationCloture = signal(false);
  erreur = signal('');
  message = signal('');
  private id: number | null = null;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.erreur.set('Identifiant invalide.');
      return;
    }
    this.id = id;
    this.charger();
  }

  charger(): void {
    if (this.id === null || this.chargement() || this.enregistrement()) return;
    this.chargement.set(true);
    this.erreur.set('');
    this.message.set('');
    this.confirmationCloture.set(false);
    this.campagne.set(null);
    this.service.consulterParId(this.id).subscribe({
      next: (campagne) => {
        this.campagne.set(campagne);
        this.chargement.set(false);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  demarrer(): void {
    if (this.campagne()?.statut === 'PLANIFIEE') this.enregistrerStatut('EN_COURS');
  }

  demanderCloture(): void {
    if (
      this.authService.possedeRole('ADMIN') &&
      !this.enregistrement() &&
      this.campagne()?.statut === 'EN_COURS'
    )
      this.confirmationCloture.set(true);
  }

  annulerCloture(): void {
    if (!this.enregistrement()) this.confirmationCloture.set(false);
  }

  cloturer(): void {
    if (this.confirmationCloture() && this.campagne()?.statut === 'EN_COURS') {
      this.enregistrerStatut('CLOTUREE');
    }
  }

  private enregistrerStatut(statut: StatutCampagne): void {
    const campagne = this.campagne();
    if (
      campagne === null ||
      campagne.parcelleIds.length === 0 ||
      this.enregistrement() ||
      !this.authService.possedeRole('ADMIN')
    )
      return;
    this.erreur.set('');
    this.message.set('');
    this.enregistrement.set(true);
    this.service.changerStatut(campagne.id, statut).subscribe({
      next: (resultat) => {
        this.campagne.set(resultat);
        this.enregistrement.set(false);
        this.confirmationCloture.set(false);
        this.message.set(
          statut === 'EN_COURS' ? 'La campagne a démarré.' : 'La campagne est clôturée.',
        );
      },
      error: (erreur: unknown) => {
        this.enregistrement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  private afficherErreur(erreur: unknown): void {
    if (erreur instanceof HttpErrorResponse && erreur.status === 409) {
      this.erreur.set(
        typeof erreur.error?.message === 'string'
          ? erreur.error.message
          : 'Le statut a changé. Actualisez la campagne.',
      );
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
      this.erreur.set('Session invalide. Connectez-vous à nouveau.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
      this.erreur.set('Vous ne disposez pas des droits nécessaires.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 404) {
      this.erreur.set('Cette campagne est introuvable.');
    } else {
      this.erreur.set('Opération non confirmée. Actualisez la campagne pour vérifier son état.');
    }
  }
}
