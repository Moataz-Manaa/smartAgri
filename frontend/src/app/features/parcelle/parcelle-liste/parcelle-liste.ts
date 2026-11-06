import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

import { ParcelleResponse } from '../../../core/models/parcelle';
import { ParcelleService } from '../../../core/services/parcelle';
import { RouterLink } from '@angular/router';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { AuthService } from '../../../core/services/auth';

@Component({
  selector: 'app-parcelle-liste',
  imports: [RouterLink, Pagination],
  templateUrl: './parcelle-liste.html',
  styleUrl: './parcelle-liste.css',
})
export class ParcelleListe implements OnInit {
  private authService = inject(AuthService);

  estAdministrateur(): boolean {
    return this.authService.possedeRole('ADMIN');
  }
  private parcelleService = inject(ParcelleService);

  parcelles = signal<ParcelleResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.chargerParcelles(0);
  }

  chargement = signal(false);

  erreur = signal('');

  ngOnInit(): void {
    this.chargerParcelles();
  }

  chargerParcelles(page: number = this.page()): void {
    if (this.chargement() && this.totalPages() > 0) return;
    this.chargement.set(true);
    this.erreur.set('');
    this.parcelles.set([]);

    this.parcelleService.consulterPage(page, this.taille()).subscribe({
      next: (donnees) => {
        this.chargement.set(false);
        if (page > 0 && donnees.contenu.length === 0) {
          this.chargerParcelles(Math.max(0, donnees.totalPages - 1));
          return;
        }
        this.parcelles.set(donnees.contenu);
        this.page.set(donnees.page);
        this.totalElements.set(donnees.totalElements);
        this.totalPages.set(donnees.totalPages);
        this.chargement.set(false);
      },

      error: (erreur: unknown) => {
        this.chargement.set(false);

        if (erreur instanceof HttpErrorResponse) {
          if (erreur.status === 401) {
            this.erreur.set('Session invalide. Connecte-toi à nouveau.');
          } else if (erreur.status === 403) {
            this.erreur.set('Tu ne disposes pas des permissions nécessaires.');
          } else if (erreur.status === 0) {
            this.erreur.set('Impossible de joindre le backend.');
          } else {
            this.erreur.set('Impossible de charger les parcelles.');
          }
        } else {
          this.erreur.set('Vérifie ta session et réessaie.');
        }
      },
    });
  }
}
