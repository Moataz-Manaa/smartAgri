import { Component, Input, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, forkJoin } from 'rxjs';
import { IrrigationResponse } from '../../../core/models/irrigation';
import { IrrigationService } from '../../../core/services/irrigation';
import { AuthService } from '../../../core/services/auth';
import { Pagination } from '../../../shared/components/pagination/pagination';

@Component({
  selector: 'app-irrigation-liste',
  imports: [DatePipe, DecimalPipe, RouterLink, Pagination],
  templateUrl: './irrigation-liste.html',
})
export class IrrigationListe implements OnChanges, OnDestroy {
  @Input() plantationId = 0;
  @Input() cloturee = false;
  authService = inject(AuthService);
  private service = inject(IrrigationService);
  private requete?: Subscription;
  irrigations = signal<IrrigationResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  volumeTotal = signal<number | null>(null);
  chargement = signal(false);
  erreur = signal('');

  ngOnChanges(): void {
    this.charger(0);
  }
  ngOnDestroy(): void {
    this.requete?.unsubscribe();
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.charger(0);
  }

  charger(page: number = this.page()): void {
    this.requete?.unsubscribe();
    this.irrigations.set([]);
    this.volumeTotal.set(null);
    this.totalElements.set(0);
    this.totalPages.set(0);
    this.erreur.set('');
    if (!Number.isSafeInteger(this.plantationId) || this.plantationId <= 0) {
      this.chargement.set(false);
      this.erreur.set('Plantation invalide.');
      return;
    }
    this.chargement.set(true);
    this.requete = forkJoin({
      page: this.service.consulterPage(this.plantationId, page, this.taille()),
      total: this.service.consulterTotal(this.plantationId),
    }).subscribe({
      next: (resultat) => {
        const donnees = resultat.page;
        this.chargement.set(false);
        if (page > 0 && donnees.contenu.length === 0) {
          this.charger(Math.max(0, donnees.totalPages - 1));
          return;
        }
        this.irrigations.set(donnees.contenu);
        this.volumeTotal.set(resultat.total.volumeTotalMetresCubes);
        this.page.set(donnees.page);
        this.totalElements.set(donnees.totalElements);
        this.totalPages.set(donnees.totalPages);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.erreur.set(
          erreur instanceof HttpErrorResponse && erreur.status === 403
            ? 'Vous ne disposez plus des droits pour consulter ces irrigations.'
            : 'Impossible de charger les irrigations. Vérifiez votre connexion et votre session.',
        );
      },
    });
  }
}
