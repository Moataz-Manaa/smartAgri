import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, Observable, of } from 'rxjs';
import { CampagneRequest, CampagneResponse, StatutCampagne } from '../../../core/models/campagne';
import { ParcelleResponse } from '../../../core/models/parcelle';
import { CampagneService } from '../../../core/services/campagne';
import { ParcelleService } from '../../../core/services/parcelle';
import { StatutCampagnePipe } from '../../../shared/pipes/statut-campagne';

@Component({
  selector: 'app-campagne-formulaire',
  imports: [FormsModule, RouterLink, StatutCampagnePipe],
  templateUrl: './campagne-formulaire.html',
  styleUrl: './campagne-formulaire.css',
})
export class CampagneFormulaire implements OnInit {
  private service = inject(CampagneService);
  private parcelleService = inject(ParcelleService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  id: number | null = null;
  nom = '';
  description = '';
  dateDebut = '';
  dateFin = '';
  statut: StatutCampagne = 'PLANIFIEE';
  parcelles = signal<ParcelleResponse[]>([]);
  parcelleIds: number[] = [];
  private associationInitialePossible = false;
  chargement = signal(false);
  enregistrement = signal(false);
  echecChargement = signal(false);
  erreur = signal('');

  ngOnInit(): void {
    const valeur = this.route.snapshot.paramMap.get('id');
    if (valeur !== null) {
      const id = Number(valeur);
      if (!Number.isSafeInteger(id) || id <= 0) {
        this.echecChargement.set(true);
        this.erreur.set('Identifiant invalide.');
        return;
      }
      this.id = id;
    }
    this.chargement.set(true);
    forkJoin({
      parcelles: this.parcelleService.consulterToutes(),
      campagne: this.id === null ? of(null) : this.service.consulterParId(this.id),
    }).subscribe({
      next: (donnees) => {
        this.parcelles.set(donnees.parcelles);
        if (donnees.campagne !== null) {
          const campagne = donnees.campagne;
          this.nom = campagne.nom;
          this.description = campagne.description ?? '';
          this.dateDebut = campagne.dateDebut;
          this.dateFin = campagne.dateFin;
          this.statut = campagne.statut;
          this.parcelleIds = campagne.parcelleIds.slice();
          this.associationInitialePossible = this.estCloturee() && this.parcelleIds.length === 0;
        }
        this.chargement.set(false);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.echecChargement.set(true);
        this.afficherErreur(erreur);
      },
    });
  }

  estCloturee(): boolean {
    return this.statut === 'CLOTUREE';
  }

  peutAssocierParcelles(): boolean {
    return this.id !== null && this.estCloturee() && this.associationInitialePossible;
  }

  formulaireBloque(): boolean {
    return (
      this.chargement() ||
      this.enregistrement() ||
      this.echecChargement() ||
      (this.estCloturee() && !this.peutAssocierParcelles()) ||
      this.parcelles().length === 0
    );
  }

  selectionnerParcelle(id: number, event: Event): void {
    if (this.formulaireBloque()) return;
    const cochee = (event.target as HTMLInputElement).checked;
    if (cochee) {
      if (!this.parcelleIds.includes(id)) this.parcelleIds.push(id);
    } else {
      const selection: number[] = [];
      for (const valeur of this.parcelleIds) {
        if (valeur !== id) selection.push(valeur);
      }
      this.parcelleIds = selection;
    }
  }

  enregistrer(formulaire: NgForm): void {
    if (this.formulaireBloque()) return;
    this.erreur.set('');
    if (this.parcelleIds.length === 0) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Sélectionnez au moins une parcelle.');
      return;
    }
    let operation: Observable<CampagneResponse>;
    if (this.peutAssocierParcelles() && this.id !== null) {
      operation = this.service.associerParcelles(this.id, this.parcelleIds.slice());
    } else {
      if (
        formulaire.invalid ||
        this.nom.trim() === '' ||
        this.dateDebut === '' ||
        this.dateFin === ''
      ) {
        formulaire.control.markAllAsTouched();
        this.erreur.set('Vérifiez les champs du formulaire.');
        return;
      }
      if (this.dateFin <= this.dateDebut) {
        this.erreur.set('La date de fin doit être postérieure à la date de début.');
        return;
      }
      const request: CampagneRequest = {
        nom: this.nom.trim(),
        description: this.description.trim() || null,
        dateDebut: this.dateDebut,
        dateFin: this.dateFin,
        parcelleIds: this.parcelleIds.slice(),
      };
      operation =
        this.id === null ? this.service.creer(request) : this.service.modifier(this.id, request);
    }
    this.enregistrement.set(true);
    operation.subscribe({
      next: (campagne) => {
        this.enregistrement.set(false);
        this.router.navigate(['/campagnes', campagne.id]);
      },
      error: (erreur: unknown) => {
        this.enregistrement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  private afficherErreur(erreur: unknown): void {
    if (erreur instanceof HttpErrorResponse && erreur.status === 409) {
      this.erreur.set(
        typeof erreur.error?.message === 'string'
          ? erreur.error.message
          : 'Enregistrement en conflit. Actualisez avant de réessayer.',
      );
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
      this.erreur.set('La gestion des campagnes est réservée à l’administrateur.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
      this.erreur.set('Session invalide. Connectez-vous à nouveau.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 404) {
      this.erreur.set('La campagne ou une parcelle sélectionnée est introuvable.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 400) {
      this.erreur.set(
        'Les informations envoyées sont invalides. Vérifiez les dates et les parcelles.',
      );
    } else {
      this.erreur.set('Impossible de terminer l’opération. Vérifiez votre connexion et réessayez.');
    }
  }
}
