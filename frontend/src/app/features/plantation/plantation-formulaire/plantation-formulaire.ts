import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { PlantationService } from '../../../core/services/plantation';
import { ParcelleService } from '../../../core/services/parcelle';
import { CampagneService } from '../../../core/services/campagne';
import { VarieteService } from '../../../core/services/variete';
import { CultureService } from '../../../core/services/culture';
import { ParcelleResponse } from '../../../core/models/parcelle';
import { CampagneResponse } from '../../../core/models/campagne';
import { CultureResponse } from '../../../core/models/culture';
import { VarieteResponse } from '../../../core/models/variete';
import { PlantationRequest, StatutPlantation } from '../../../core/models/plantation';

@Component({
  selector: 'app-plantation-formulaire',
  imports: [FormsModule, RouterLink],
  templateUrl: './plantation-formulaire.html',
  styleUrl: './plantation-formulaire.css',
})
export class PlantationFormulaire implements OnInit {
  private service = inject(PlantationService);
  private parcelleService = inject(ParcelleService);
  private campagneService = inject(CampagneService);
  private varieteService = inject(VarieteService);
  private cultureService = inject(CultureService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  parcelles = signal<ParcelleResponse[]>([]);
  campagnes = signal<CampagneResponse[]>([]);
  varietes = signal<VarieteResponse[]>([]);
  cultures = signal<CultureResponse[]>([]);
  chargement = signal(true);
  enregistrement = signal(false);
  echecChargement = signal(false);
  cloturee = signal(false);
  erreur = signal('');
  id: number | null = null;
  parcelleId: number | null = null;
  campagneId: number | null = null;
  cultureId: number | null = null;
  varieteId: number | null = null;
  datePlantation = '';
  superficie: number | null = null;
  quantitePlantee: number | null = null;
  dateRecoltePrevue = '';
  commentaire = '';
  statut: StatutPlantation = 'EN_COURS';

  ngOnInit(): void {
    const valeur = this.route.snapshot.paramMap.get('id');
    if (valeur !== null) {
      this.id = Number(valeur);
      if (!Number.isSafeInteger(this.id) || this.id <= 0) {
        this.chargement.set(false);
        this.echecChargement.set(true);
        this.erreur.set('Identifiant invalide.');
        return;
      }
    }
    forkJoin({
      parcelles: this.parcelleService.consulterToutes(),
      campagnes: this.campagneService.consulterToutes(),
      varietes: this.varieteService.consulterToutes(),
      cultures: this.cultureService.consulterToutes(),
      plantation: this.id === null ? of(null) : this.service.consulterParId(this.id),
    }).subscribe({
      next: (donnees) => {
        this.parcelles.set(donnees.parcelles);
        this.campagnes.set(donnees.campagnes);
        this.varietes.set(donnees.varietes);
        this.cultures.set(donnees.cultures);
        const plantation = donnees.plantation;
        if (plantation !== null) {
          this.parcelleId = plantation.parcelleId;
          this.campagneId = plantation.campagneId;
          this.cultureId = plantation.cultureId;
          this.varieteId = plantation.varieteId;
          this.datePlantation = plantation.datePlantation;
          this.superficie = plantation.superficie;
          this.quantitePlantee = plantation.quantitePlantee;
          this.dateRecoltePrevue = plantation.dateRecoltePrevue ?? '';
          this.commentaire = plantation.commentaire ?? '';
          this.statut = plantation.statut;
          this.cloturee.set(plantation.campagneCloturee);
        } else {
          const parcelleId = Number(this.route.snapshot.queryParamMap.get('parcelleId'));
          for (const parcelle of donnees.parcelles) {
            if (parcelle.id === parcelleId) this.parcelleId = parcelleId;
          }
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

  campagnesDisponibles(): CampagneResponse[] {
    const resultat: CampagneResponse[] = [];
    for (const campagne of this.campagnes()) {
      if (
        this.parcelleId !== null &&
        campagne.parcelleIds.includes(this.parcelleId) &&
        (campagne.statut !== 'CLOTUREE' || campagne.id === this.campagneId)
      )
        resultat.push(campagne);
    }
    return resultat;
  }

  varietesDisponibles(): VarieteResponse[] {
    const resultat: VarieteResponse[] = [];
    for (const variete of this.varietes()) {
      if (variete.cultureId === this.cultureId) resultat.push(variete);
    }
    return resultat;
  }

  changerParcelle(): void {
    this.campagneId = null;
  }
  changerCulture(): void {
    this.varieteId = null;
  }

  superficieMax(): number | null {
    for (const parcelle of this.parcelles()) {
      if (parcelle.id === this.parcelleId) return parcelle.superficie;
    }
    return null;
  }

  enregistrer(formulaire: NgForm): void {
    if (this.chargement() || this.enregistrement() || this.echecChargement() || this.cloturee())
      return;
    this.erreur.set('');
    if (
      formulaire.invalid ||
      this.parcelleId === null ||
      this.campagneId === null ||
      this.varieteId === null ||
      this.superficie === null ||
      !Number.isFinite(this.superficie)
    ) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Vérifiez les champs obligatoires du formulaire.');
      return;
    }
    let campagneChoisie: CampagneResponse | null = null;
    for (const campagne of this.campagnesDisponibles()) {
      if (campagne.id === this.campagneId && campagne.statut !== 'CLOTUREE')
        campagneChoisie = campagne;
    }
    let varieteValide = false;
    for (const variete of this.varietesDisponibles()) {
      if (variete.id === this.varieteId) varieteValide = true;
    }
    if (campagneChoisie === null || !varieteValide) {
      this.erreur.set(
        'Choisissez une campagne ouverte de la parcelle et une variété de la culture.',
      );
      return;
    }
    if (
      this.datePlantation > campagneChoisie.dateFin ||
      (this.dateRecoltePrevue !== '' &&
        (this.dateRecoltePrevue < this.datePlantation ||
          this.dateRecoltePrevue < campagneChoisie.dateDebut ||
          this.dateRecoltePrevue > campagneChoisie.dateFin))
    ) {
      this.erreur.set('Vérifiez les dates de plantation et de récolte par rapport à la campagne.');
      return;
    }
    const request: PlantationRequest = {
      parcelleId: this.parcelleId,
      campagneId: this.campagneId,
      varieteId: this.varieteId,
      datePlantation: this.datePlantation,
      superficie: this.superficie,
      quantitePlantee: this.quantitePlantee,
      dateRecoltePrevue: this.dateRecoltePrevue || null,
      commentaire: this.commentaire.trim() || null,
      statut: this.statut,
    };
    const operation =
      this.id === null ? this.service.creer(request) : this.service.modifier(this.id, request);
    this.enregistrement.set(true);
    operation.subscribe({
      next: (plantation) => {
        this.enregistrement.set(false);
        this.router.navigate(['/plantations', plantation.id]);
      },
      error: (erreur: unknown) => {
        this.enregistrement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  private afficherErreur(erreur: unknown): void {
    if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
      this.erreur.set('Action réservée aux agriculteurs affectés à cette parcelle.');
    } else if (
      erreur instanceof HttpErrorResponse &&
      (erreur.status === 400 || erreur.status === 409) &&
      typeof erreur.error?.message === 'string'
    ) {
      this.erreur.set(erreur.error.message);
    } else {
      this.erreur.set(
        'Impossible de terminer l’opération. Vérifiez votre connexion, votre session et les données sélectionnées.',
      );
    }
  }
}
