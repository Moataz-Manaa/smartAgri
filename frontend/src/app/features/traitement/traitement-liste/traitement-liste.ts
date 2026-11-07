import { Component, Input, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { TraitementResponse } from '../../../core/models/traitement';
import { TraitementService } from '../../../core/services/traitement';
import { AuthService } from '../../../core/services/auth';
import { UniteQuantitePipe } from '../../../shared/pipes/unite-quantite';
import { Pagination } from '../../../shared/components/pagination/pagination';

@Component({
  selector: 'app-traitement-liste',
  imports: [DatePipe, DecimalPipe, RouterLink, Pagination, UniteQuantitePipe],
  templateUrl: './traitement-liste.html',
})
export class TraitementListe implements OnChanges, OnDestroy {
  @Input() plantationId = 0;
  @Input() cloturee = false;
  authService = inject(AuthService);
  private service = inject(TraitementService);
  private requete?: Subscription;
  traitements = signal<TraitementResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
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
    this.traitements.set([]);
    this.totalElements.set(0);
    this.totalPages.set(0);
    this.erreur.set('');
    if (!Number.isSafeInteger(this.plantationId) || this.plantationId <= 0) {
      this.chargement.set(false);
      this.erreur.set('Plantation invalide.');
      return;
    }
    this.chargement.set(true);
    this.requete = this.service.consulterPage(this.plantationId, page, this.taille()).subscribe({
      next: (donnees) => {
        this.chargement.set(false);
        if (page > 0 && donnees.contenu.length === 0) {
          this.charger(Math.max(0, donnees.totalPages - 1));
          return;
        }
        this.traitements.set(donnees.contenu);
        this.page.set(donnees.page);
        this.totalElements.set(donnees.totalElements);
        this.totalPages.set(donnees.totalPages);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.erreur.set(
          erreur instanceof HttpErrorResponse && erreur.status === 403
            ? 'Vous ne disposez plus des droits pour consulter ces traitements.'
            : 'Impossible de charger les traitements. Vérifiez votre connexion et votre session.',
        );
      },
    });
  }
}
