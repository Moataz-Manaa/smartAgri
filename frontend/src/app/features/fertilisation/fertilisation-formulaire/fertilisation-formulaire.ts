import { UniteQuantite } from '../../../core/models/unite-quantite';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FertilisationService } from '../../../core/services/fertilisation';
import { PlantationService } from '../../../core/services/plantation';
import { PlantationResponse } from '../../../core/models/plantation';
import { FertilisationRequest } from '../../../core/models/fertilisation';

@Component({
  selector: 'app-fertilisation-formulaire',
  imports: [FormsModule, RouterLink],
  templateUrl: './fertilisation-formulaire.html',
  styleUrl: './fertilisation-formulaire.css',
})
export class FertilisationFormulaire implements OnInit {
  private service = inject(FertilisationService);
  private plantationService = inject(PlantationService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  plantation = signal<PlantationResponse | null>(null);
  chargement = signal(true);
  enregistrement = signal(false);
  echecChargement = signal(false);
  erreur = signal('');
  id: number | null = null;
  dateFertilisation = '';
  commentaire = '';
  quantite: number | null = null;
  unite: UniteQuantite = 'KILOGRAMME';
  auteur = '';
  nomProduit = '';
  methodeApplication = '';
  typeEngrais = '';
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
      next: (fertilisation) => {
        this.dateFertilisation = fertilisation.dateFertilisation;
        this.commentaire = fertilisation.commentaire ?? '';
        this.quantite = fertilisation.quantite;
        this.unite = fertilisation.unite;
        this.nomProduit = fertilisation.nomProduit;
        this.methodeApplication = fertilisation.methodeApplication ?? '';
        this.typeEngrais = fertilisation.typeEngrais ?? '';
        this.auteur = fertilisation.auteurPrenom + ' ' + fertilisation.auteurNom;
        this.chargerPlantation(fertilisation.plantationId);
      },
      error: (erreur: unknown) => this.erreurChargement(erreur),
    });
  }

  private chargerPlantation(id: number): void {
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.echecChargement.set(true);
      this.chargement.set(false);
      this.erreur.set('Ouvrez une plantation pour ajouter une fertilisation.');
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
      this.nomProduit.trim() === '' ||
      this.quantite === null ||
      !Number.isFinite(this.quantite) ||
      this.quantite <= 0 ||
      this.dateFertilisation > this.aujourdhui ||
      this.dateFertilisation < plantation.datePlantation
    ) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Vérifiez le produit, la date, la quantité et l’unité de la fertilisation.');
      return;
    }
    const request: FertilisationRequest = {
      plantationId: plantation.id,
      dateFertilisation: this.dateFertilisation,
      quantite: this.quantite,
      unite: this.unite,
      nomProduit: this.nomProduit.trim(),
      methodeApplication: this.methodeApplication.trim() || null,
      typeEngrais: this.typeEngrais.trim() || null,
      commentaire: this.commentaire.trim() || null,
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
