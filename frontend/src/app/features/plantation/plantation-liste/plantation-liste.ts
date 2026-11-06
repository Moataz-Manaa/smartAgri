import { Component, Input, OnChanges, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { PlantationService } from '../../../core/services/plantation';
import { CampagneService } from '../../../core/services/campagne';
import { CampagneResponse } from '../../../core/models/campagne';
import { PlantationResponse, StatutPlantation } from '../../../core/models/plantation';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { StatutPlantationPipe } from '../../../shared/pipes/statut-plantation';

@Component({
  selector: 'app-plantation-liste',
  imports: [FormsModule, RouterLink, Pagination, StatutPlantationPipe],
  templateUrl: './plantation-liste.html',
})
export class PlantationListe implements OnInit, OnChanges, OnDestroy {
  @Input() parcelleId: number | null = null;
  authService = inject(AuthService);
  private service = inject(PlantationService);
  private campagneService = inject(CampagneService);
  private requete?: Subscription;
  private requeteCampagnes?: Subscription;
  plantations = signal<PlantationResponse[]>([]);
  campagnes = signal<CampagneResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  chargement = signal(false);
  erreur = signal('');
  campagneId: number | null = null;
  statut: StatutPlantation | null = null;

  ngOnInit(): void {
    this.requeteCampagnes = this.campagneService.consulterToutes().subscribe({
      next: (campagnes) => this.campagnes.set(campagnes),
      error: () => this.campagnes.set([]),
    });
    if (this.parcelleId === null) this.charger(0);
  }

  ngOnChanges(): void {
    this.campagneId = null;
    this.charger(0);
  }

  ngOnDestroy(): void {
    this.requete?.unsubscribe();
    this.requeteCampagnes?.unsubscribe();
  }

  campagnesDisponibles(): CampagneResponse[] {
    if (this.parcelleId === null) return this.campagnes();
    const resultat: CampagneResponse[] = [];
    for (const campagne of this.campagnes()) {
      if (campagne.parcelleIds.includes(this.parcelleId)) resultat.push(campagne);
    }
    return resultat;
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.charger(0);
  }

  charger(page: number = this.page()): void {
    this.requete?.unsubscribe();
    this.chargement.set(true);
    this.erreur.set('');
    this.plantations.set([]);
    this.totalElements.set(0);
    this.totalPages.set(0);
    this.requete = this.service
      .consulterPage(page, this.taille(), this.parcelleId, this.campagneId, this.statut)
      .subscribe({
        next: (donnees) => {
          this.chargement.set(false);
          if (page > 0 && donnees.contenu.length === 0) {
            this.charger(Math.max(0, donnees.totalPages - 1));
            return;
          }
          this.plantations.set(donnees.contenu);
          this.page.set(donnees.page);
          this.totalElements.set(donnees.totalElements);
          this.totalPages.set(donnees.totalPages);
        },
        error: (erreur: unknown) => {
          this.chargement.set(false);
          if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
            this.erreur.set('Vous ne disposez plus des droits pour cette sélection.');
          } else {
            this.erreur.set(
              'Impossible de charger les plantations. Vérifiez votre connexion et votre session.',
            );
          }
        },
      });
  }
}
