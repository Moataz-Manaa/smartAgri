import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ObservationService } from '../../../core/services/observation';
import { PlantationService } from '../../../core/services/plantation';
import { PlantationResponse } from '../../../core/models/plantation';
import { ObservationRequest } from '../../../core/models/observation';

@Component({
  selector: 'app-observation-formulaire',
  imports: [FormsModule, RouterLink],
  templateUrl: './observation-formulaire.html',
  styleUrl: './observation-formulaire.css',
})
export class ObservationFormulaire implements OnInit {
  private service = inject(ObservationService);
  private plantationService = inject(PlantationService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  plantation = signal<PlantationResponse | null>(null);
  chargement = signal(true);
  enregistrement = signal(false);
  echecChargement = signal(false);
  erreur = signal('');
  id: number | null = null;
  dateObservation = '';
  description = '';
  auteur = '';
  aujourdhui = this.dateLocale();

  ngOnInit(): void {
    const valeur = this.route.snapshot.paramMap.get('id');
    if (valeur === null) {
      this.chargerPlantation(Number(this.route.snapshot.queryParamMap.get('plantationId')));
      return;
    }
    this.id = Number(valeur);
    if (!Number.isSafeInteger(this.id) || this.id <= 0) {
      this.echecChargement.set(true);
      this.chargement.set(false);
      this.erreur.set('Identifiant invalide.');
      return;
    }
    this.service.consulterParId(this.id).subscribe({
      next: (observation) => {
        this.dateObservation = observation.dateObservation;
        this.description = observation.description;
        this.auteur = observation.auteurPrenom + ' ' + observation.auteurNom;
        this.chargerPlantation(observation.plantationId);
      },
      error: (erreur: unknown) => this.erreurChargement(erreur),
    });
  }

  private chargerPlantation(id: number): void {
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.echecChargement.set(true);
      this.chargement.set(false);
      this.erreur.set('Ouvrez une plantation pour ajouter une observation.');
      return;
    }
    this.plantationService.consulterParId(id).subscribe({
      next: (plantation) => {
        this.plantation.set(plantation);
        this.chargement.set(false);
      },
      error: (erreur: unknown) => this.erreurChargement(erreur),
    });
  }

  enregistrer(formulaire: NgForm): void {
    const plantation = this.plantation();
    if (
      this.chargement() ||
      this.enregistrement() ||
      this.echecChargement() ||
      plantation === null ||
      plantation.campagneCloturee
    )
      return;
    this.erreur.set('');
    if (
      formulaire.invalid ||
      this.description.trim() === '' ||
      this.dateObservation > this.aujourdhui ||
      this.dateObservation < plantation.datePlantation
    ) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Vérifiez la date et la description de l’observation.');
      return;
    }
    const request: ObservationRequest = {
      plantationId: plantation.id,
      dateObservation: this.dateObservation,
      description: this.description.trim(),
    };
    const operation =
      this.id === null ? this.service.creer(request) : this.service.modifier(this.id, request);
    this.enregistrement.set(true);
    operation.subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.router.navigate(['/plantations', plantation.id]);
      },
      error: (erreur: unknown) => {
        this.enregistrement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  private erreurChargement(erreur: unknown): void {
    this.chargement.set(false);
    this.echecChargement.set(true);
    this.afficherErreur(erreur);
  }

  private afficherErreur(erreur: unknown): void {
    if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
      this.erreur.set('Vous ne disposez plus des droits pour cette plantation.');
    } else if (
      erreur instanceof HttpErrorResponse &&
      (erreur.status === 400 || erreur.status === 409) &&
      typeof erreur.error?.message === 'string'
    ) {
      this.erreur.set(erreur.error.message);
    } else {
      this.erreur.set(
        'Impossible de terminer l’opération. Vérifiez votre connexion, votre session et les données saisies.',
      );
    }
  }

  private dateLocale(): string {
    const date = new Date();
    return (
      date.getFullYear() +
      '-' +
      String(date.getMonth() + 1).padStart(2, '0') +
      '-' +
      String(date.getDate()).padStart(2, '0')
    );
  }
}
