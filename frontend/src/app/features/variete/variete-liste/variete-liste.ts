import { Component, inject, OnInit, signal, OnDestroy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { CultureResponse } from '../../../core/models/culture';
import { CultureService } from '../../../core/services/culture';
import { VarieteResponse } from '../../../core/models/variete';
import { VarieteService } from '../../../core/services/variete';
import { AuthService } from '../../../core/services/auth';
import { Pagination } from '../../../shared/components/pagination/pagination';

@Component({
  selector: 'app-variete-liste',
  imports: [RouterLink, Pagination, FormsModule],
  templateUrl: './variete-liste.html',
})
export class VarieteListe implements OnInit, OnDestroy {
  authService = inject(AuthService);
  private service = inject(VarieteService);
  private cultureService = inject(CultureService);
  private route = inject(ActivatedRoute);
  private abonnement: Subscription | null = null;
  private requete: Subscription | null = null;
  cultures = signal<CultureResponse[]>([]);
  cultureId: number | null = null;

  varietes = signal<VarieteResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  chargement = signal(false);
  erreur = signal('');
  erreurCultures = signal('');

  ngOnInit(): void {
    this.cultureService.consulterToutes().subscribe({
      next: (cultures) => this.cultures.set(cultures),
      error: () => this.erreurCultures.set('Impossible de charger les cultures du filtre.'),
    });
    this.abonnement = this.route.queryParamMap.subscribe((params) => {
      const valeur = params.get('cultureId');
      const id = valeur === null ? null : Number(valeur);
      if (id !== null && (!Number.isSafeInteger(id) || id <= 0)) {
        this.requete?.unsubscribe();
        this.chargement.set(false);
        this.varietes.set([]);
        this.erreur.set('Le filtre de culture est invalide.');
        return;
      }
      this.cultureId = id;
      this.charger(0);
    });
  }

  ngOnDestroy(): void {
    this.abonnement?.unsubscribe();
    this.requete?.unsubscribe();
  }

  changerFiltre(cultureId: number | null): void {
    this.cultureId = cultureId;
    this.charger(0);
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.charger(0);
  }

  charger(page: number = this.page()): void {
    this.requete?.unsubscribe();
    this.chargement.set(true);
    this.erreur.set('');
    this.varietes.set([]);
    this.requete = this.service.consulterPage(page, this.taille(), this.cultureId).subscribe({
      next: (donnees) => {
        this.chargement.set(false);
        if (page > 0 && donnees.contenu.length === 0) {
          this.charger(Math.max(0, donnees.totalPages - 1));
          return;
        }
        this.varietes.set(donnees.contenu);
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
        } else if (erreur instanceof HttpErrorResponse && erreur.status === 404) {
          this.erreur.set('La culture du filtre est introuvable. Choisissez une autre culture.');
        } else {
          this.erreur.set(
            'Impossible de charger les variétés. Vérifiez votre connexion et réessayez.',
          );
        }
      },
    });
  }
}
