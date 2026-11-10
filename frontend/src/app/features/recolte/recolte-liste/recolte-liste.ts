import { Component, Input, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, forkJoin } from 'rxjs';
import { RecolteResponse } from '../../../core/models/recolte';
import { RecolteService } from '../../../core/services/recolte';
import { AuthService } from '../../../core/services/auth';
import { Pagination } from '../../../shared/components/pagination/pagination';

@Component({
  selector: 'app-recolte-liste',
  imports: [DatePipe, DecimalPipe, RouterLink, Pagination],
  templateUrl: './recolte-liste.html',
})
export class RecolteListe implements OnChanges, OnDestroy {
  @Input() plantationId = 0;
  @Input() cloturee = false;
  authService = inject(AuthService);
  private service = inject(RecolteService);
  private requete?: Subscription;
  recoltes = signal<RecolteResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  quantiteTotale = signal<number | null>(null);
  rendement = signal<number | null>(null);
  superficie = signal<number | null>(null);
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
    this.recoltes.set([]);
    this.quantiteTotale.set(null);
    this.rendement.set(null);
    this.superficie.set(null);
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
        this.recoltes.set(donnees.contenu);
        this.quantiteTotale.set(resultat.total.quantiteTotaleKilogrammes);
        this.rendement.set(resultat.total.rendementKgParHectare);
        this.superficie.set(resultat.total.superficieHectares);
        this.page.set(donnees.page);
        this.totalElements.set(donnees.totalElements);
        this.totalPages.set(donnees.totalPages);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.erreur.set(
          erreur instanceof HttpErrorResponse && erreur.status === 403
            ? 'Vous ne disposez plus des droits pour consulter ces récoltes.'
            : 'Impossible de charger les récoltes. Vérifiez votre connexion et votre session.',
        );
      },
    });
  }
}
