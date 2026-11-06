import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { CampagneService } from '../../../core/services/campagne';
import { CampagneResponse, StatutCampagne } from '../../../core/models/campagne';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { StatutCampagnePipe } from '../../../shared/pipes/statut-campagne';

@Component({
  selector: 'app-campagne-liste',
  imports: [DatePipe, FormsModule, RouterLink, Pagination, StatutCampagnePipe],
  templateUrl: './campagne-liste.html',
})
export class CampagneListe implements OnInit {
  authService = inject(AuthService);
  private service = inject(CampagneService);
  campagnes = signal<CampagneResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  chargement = signal(false);
  erreur = signal('');
  statut: StatutCampagne | null = null;

  ngOnInit(): void {
    this.charger();
  }

  changerFiltre(statut: StatutCampagne | null): void {
    this.statut = statut;
    this.charger(0);
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.charger(0);
  }

  charger(page: number = this.page()): void {
    if (this.chargement()) return;
    this.chargement.set(true);
    this.erreur.set('');
    this.campagnes.set([]);
    this.service.consulterPage(page, this.taille(), this.statut).subscribe({
      next: (donnees) => {
        this.chargement.set(false);
        if (page > 0 && donnees.contenu.length === 0) {
          this.charger(Math.max(0, donnees.totalPages - 1));
          return;
        }
        this.campagnes.set(donnees.contenu);
        this.page.set(donnees.page);
        this.totalElements.set(donnees.totalElements);
        this.totalPages.set(donnees.totalPages);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
          this.erreur.set('Session invalide. Connectez-vous à nouveau.');
        } else if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
          this.erreur.set('Vous ne disposez pas des droits nécessaires.');
        } else {
          this.erreur.set(
            'Impossible de charger les campagnes. Vérifiez votre connexion et réessayez.',
          );
        }
      },
    });
  }
}
