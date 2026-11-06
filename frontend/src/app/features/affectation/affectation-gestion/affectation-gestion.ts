import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AffectationResponse } from '../../../core/models/affectation';
import { ParcelleResponse } from '../../../core/models/parcelle';
import { UtilisateurResponse } from '../../../core/models/utilisateur';
import { AffectationService } from '../../../core/services/affectation';
import { ParcelleService } from '../../../core/services/parcelle';
import { UtilisateurService } from '../../../core/services/utilisateur';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { PageResponse } from '../../../core/models/page';

@Component({
  selector: 'app-affectation-gestion',
  imports: [FormsModule, Pagination],
  templateUrl: './affectation-gestion.html',
  styleUrl: './affectation-gestion.css',
})
export class AffectationGestion implements OnInit {
  private affectationService = inject(AffectationService);
  private parcelleService = inject(ParcelleService);
  private utilisateurService = inject(UtilisateurService);

  parcelles = signal<ParcelleResponse[]>([]);
  utilisateurs = signal<UtilisateurResponse[]>([]);
  affectations = signal<AffectationResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  chargement = signal(true);
  enregistrement = signal(false);
  retraitEnCours = signal<number | null>(null);
  erreur = signal('');
  message = signal('');
  parcelleId: number | null = null;
  utilisateurId: number | null = null;
  filtreParcelleId: number | null = null;

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    if (this.enregistrement() || this.retraitEnCours() !== null) {
      return;
    }
    this.chargement.set(true);
    this.erreur.set('');
    // Charger les trois listes ensemble avant de rendre le formulaire disponible.
    forkJoin({
      parcelles: this.parcelleService.consulterToutes(),
      utilisateurs: this.utilisateurService.consulterTous(),
      affectations: this.affectationService.consulterPage(
        this.page(),
        this.taille(),
        this.filtreParcelleId,
      ),
    }).subscribe({
      next: (donnees) => {
        this.parcelles.set(donnees.parcelles);
        const eligibles: UtilisateurResponse[] = [];
        for (const utilisateur of donnees.utilisateurs) {
          if (
            utilisateur.actif &&
            (utilisateur.role === 'AGRICULTEUR' || utilisateur.role === 'AGRONOME')
          ) {
            eligibles.push(utilisateur);
          }
        }
        this.utilisateurs.set(eligibles);
        this.chargement.set(false);
        this.mettreAJourPage(donnees.affectations);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  operationEnCours(): boolean {
    return this.chargement() || this.enregistrement() || this.retraitEnCours() !== null;
  }

  chargerPage(page: number): void {
    if (this.operationEnCours()) return;
    this.chargement.set(true);
    this.erreur.set('');
    this.affectations.set([]);
    this.affectationService.consulterPage(page, this.taille(), this.filtreParcelleId).subscribe({
      next: (donnees) => {
        this.chargement.set(false);
        this.mettreAJourPage(donnees);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  changerFiltre(parcelleId: number | null): void {
    this.filtreParcelleId = parcelleId;
    this.chargerPage(0);
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.chargerPage(0);
  }

  private mettreAJourPage(donnees: PageResponse<AffectationResponse>): void {
    if (donnees.page > 0 && donnees.contenu.length === 0) {
      this.chargerPage(Math.max(0, donnees.totalPages - 1));
      return;
    }
    this.affectations.set(donnees.contenu);
    this.page.set(donnees.page);
    this.totalElements.set(donnees.totalElements);
    this.totalPages.set(donnees.totalPages);
  }

  affecter(formulaire: NgForm): void {
    if (this.operationEnCours()) {
      return;
    }
    this.erreur.set('');
    this.message.set('');
    if (formulaire.invalid || this.parcelleId === null || this.utilisateurId === null) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Sélectionnez une parcelle et un utilisateur.');
      return;
    }
    this.enregistrement.set(true);
    this.affectationService
      .creer({
        parcelleId: this.parcelleId,
        utilisateurId: this.utilisateurId,
      })
      .subscribe({
        next: () => {
          this.utilisateurId = null;
          this.enregistrement.set(false);
          formulaire.controls['utilisateurId']?.reset(null);
          this.message.set(
            'Affectation ajoutée. Vous pouvez affecter une autre personne à la même parcelle.',
          );
          this.chargerPage(0);
        },
        error: (erreur: unknown) => {
          this.enregistrement.set(false);
          this.afficherErreur(erreur);
        },
      });
  }

  retirer(affectation: AffectationResponse): void {
    if (this.operationEnCours()) {
      return;
    }
    this.erreur.set('');
    this.message.set('');
    this.retraitEnCours.set(affectation.id);
    this.affectationService.retirer(affectation.id).subscribe({
      next: () => {
        this.retraitEnCours.set(null);
        this.message.set('Cette affectation a été retirée. Les autres accès sont conservés.');
        this.chargerPage(this.page());
      },
      error: (erreur: unknown) => {
        this.retraitEnCours.set(null);
        this.afficherErreur(erreur);
      },
    });
  }

  private afficherErreur(erreur: unknown): void {
    if (erreur instanceof HttpErrorResponse) {
      if (erreur.status === 409) {
        this.erreur.set(
          'Cette affectation existe déjà ou les données ont changé. Actualisez la liste.',
        );
      } else if (erreur.status === 400) {
        this.erreur.set(
          'Seuls les agriculteurs et agronomes actifs peuvent être affectés. Vérifiez les sélections.',
        );
      } else if (erreur.status === 403) {
        this.erreur.set('La gestion des affectations est réservée aux administrateurs.');
      } else if (erreur.status === 401) {
        this.erreur.set('Session invalide ou compte désactivé. Connectez-vous à nouveau.');
      } else if (erreur.status === 404) {
        this.erreur.set('Une donnée a été retirée. Actualisez la liste avant de réessayer.');
      } else {
        this.erreur.set(
          'Opération non confirmée. Vérifiez le backend et Keycloak, puis actualisez.',
        );
      }
    } else {
      this.erreur.set('Vérifiez votre session et réessayez.');
    }
  }
}
