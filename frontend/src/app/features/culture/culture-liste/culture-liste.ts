import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { CultureResponse } from '../../../core/models/culture';
import { CultureService } from '../../../core/services/culture';
import { AuthService } from '../../../core/services/auth';
import { Pagination } from '../../../shared/components/pagination/pagination';

@Component({
  selector: 'app-culture-liste',
  imports: [RouterLink, Pagination],
  templateUrl: './culture-liste.html',
})
export class CultureListe implements OnInit {
  authService = inject(AuthService);
  private service = inject(CultureService);

  cultures = signal<CultureResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  chargement = signal(false);
  erreur = signal('');

  ngOnInit(): void {
    this.charger();
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.charger(0);
  }

  charger(page: number = this.page()): void {
    if (this.chargement()) return;
    this.chargement.set(true);
    this.erreur.set('');
    this.cultures.set([]);
    this.service.consulterPage(page, this.taille()).subscribe({
      next: (donnees) => {
        this.chargement.set(false);
        if (page > 0 && donnees.contenu.length === 0) {
          this.charger(Math.max(0, donnees.totalPages - 1));
          return;
        }
        this.cultures.set(donnees.contenu);
        this.page.set(donnees.page);
        this.totalElements.set(donnees.totalElements);
        this.totalPages.set(donnees.totalPages);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
          this.erreur.set('Vous ne disposez pas des droits nécessaires.');
        } else if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
          this.erreur.set('Session invalide. Connectez-vous à nouveau.');
        } else {
          this.erreur.set(
            'Impossible de charger les cultures. Vérifiez votre connexion et réessayez.',
          );
        }
      },
    });
  }
}
