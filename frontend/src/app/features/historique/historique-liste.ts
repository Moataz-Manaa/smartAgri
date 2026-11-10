import { Component, Input, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { ActiviteResponse, TypeActivite } from '../../core/models/historique';
import { HistoriqueService } from '../../core/services/historique';
import { Pagination } from '../../shared/components/pagination/pagination';

@Component({
  selector: 'app-historique-liste',
  imports: [DatePipe, DecimalPipe, FormsModule, Pagination],
  templateUrl: './historique-liste.html',
  styleUrl: './historique-liste.css',
})
export class HistoriqueListe implements OnChanges, OnDestroy {
  @Input() plantationId = 0;
  private service = inject(HistoriqueService);
  private requete?: Subscription;
  activites = signal<ActiviteResponse[]>([]);
  chargement = signal(false);
  erreur = signal('');
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  type: TypeActivite | '' = '';
  dateDebut = '';
  dateFin = '';
  libelles: Record<TypeActivite, string> = {
    OBSERVATION: 'Observation',
    IRRIGATION: 'Irrigation',
    FERTILISATION: 'Fertilisation',
    TRAITEMENT: 'Traitement',
    RECOLTE: 'Récolte',
  };
  unites: Record<string, string> = {
    GRAMME: 'g',
    KILOGRAMME: 'kg',
    MILLILITRE: 'mL',
    LITRE: 'L',
    METRE_CUBE: 'm³',
    TONNE: 't',
  };

  ngOnChanges(): void {
    this.type = '';
    this.dateDebut = '';
    this.dateFin = '';
    this.charger();
  }

  charger(page = 0): void {
    this.requete?.unsubscribe();
    this.activites.set([]);
    this.totalElements.set(0);
    this.totalPages.set(0);
    this.erreur.set('');
    this.page.set(page);
    this.chargement.set(false);
    if (this.dateDebut && this.dateFin && this.dateDebut > this.dateFin) {
      this.erreur.set('La date de début doit précéder ou être égale à la date de fin.');
      return;
    }
    this.chargement.set(true);
    this.requete = this.service
      .consulterPage(
        this.plantationId,
        page,
        this.taille(),
        this.type,
        this.dateDebut,
        this.dateFin,
      )
      .subscribe({
        next: (resultat) => {
          this.activites.set(resultat.contenu);
          this.page.set(resultat.page);
          this.totalElements.set(resultat.totalElements);
          this.totalPages.set(resultat.totalPages);
          this.chargement.set(false);
        },
        error: (erreur: HttpErrorResponse) => {
          this.chargement.set(false);
          this.erreur.set(
            erreur.status === 403
              ? 'Vos droits ne permettent plus de consulter cet historique.'
              : 'Impossible de charger l’historique. Vérifiez les filtres et votre connexion.',
          );
        },
      });
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.charger();
  }

  reinitialiser(): void {
    this.type = '';
    this.dateDebut = '';
    this.dateFin = '';
    this.charger();
  }

  ngOnDestroy(): void {
    this.requete?.unsubscribe();
  }
}
