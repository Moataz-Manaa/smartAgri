import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ExploitationResponse } from '../../../core/models/exploitation';
import { ParcelleRequest } from '../../../core/models/parcelle';
import { ExploitationService } from '../../../core/services/exploitation';
import { ParcelleService } from '../../../core/services/parcelle';

@Component({
  selector: 'app-parcelle-formulaire',
  imports: [FormsModule, RouterLink],
  templateUrl: './parcelle-formulaire.html',
  styleUrl: './parcelle-formulaire.css',
})
export class ParcelleFormulaire implements OnInit {
  private exploitationService = inject(ExploitationService);
  private parcelleService = inject(ParcelleService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  exploitations = signal<ExploitationResponse[]>([]);

  chargement = signal(true);
  enregistrement = signal(false);
  echecChargement = signal(false);
  erreur = signal('');

  id: number | null = null;
  code = '';
  nom = '';
  superficie: number | null = null;
  typeSol = '';
  typeIrrigation = '';
  description = '';
  exploitationId: number | null = null;

  ngOnInit(): void {
    const idDansUrl = this.route.snapshot.paramMap.get('id');

    if (idDansUrl !== null) {
      const id = Number(idDansUrl);

      if (!Number.isSafeInteger(id) || id <= 0) {
        this.chargement.set(false);
        this.echecChargement.set(true);
        this.erreur.set('L’identifiant de la parcelle est invalide.');
        return;
      }

      this.id = id;
    }

    this.chargerExploitations();
  }

  private chargerExploitations(): void {
    this.exploitationService.consulterToutes().subscribe({
      next: (donnees) => {
        this.exploitations.set(donnees);

        if (this.id !== null) {
          this.chargerParcelle(this.id);
        } else {
          this.chargement.set(false);
        }
      },

      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.echecChargement.set(true);
        this.afficherErreur(erreur);
      },
    });
  }

  private chargerParcelle(id: number): void {
    this.parcelleService.consulterParId(id).subscribe({
      next: (parcelle) => {
        this.code = parcelle.code;
        this.nom = parcelle.nom;
        this.superficie = parcelle.superficie;
        this.typeSol = parcelle.typeSol ?? '';
        this.typeIrrigation = parcelle.typeIrrigation ?? '';
        this.description = parcelle.description ?? '';
        this.exploitationId = parcelle.exploitationId;

        this.chargement.set(false);
      },

      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.echecChargement.set(true);
        this.afficherErreur(erreur);
      },
    });
  }

  formulaireBloque(): boolean {
    return (
      this.chargement() ||
      this.enregistrement() ||
      this.echecChargement() ||
      this.exploitations().length === 0
    );
  }

  enregistrer(formulaire: NgForm): void {
    if (this.formulaireBloque()) {
      return;
    }

    this.erreur.set('');

    if (
      formulaire.invalid ||
      this.nom.trim() === '' ||
      this.superficie === null ||
      this.superficie <= 0 ||
      this.exploitationId === null
    ) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Vérifie les champs du formulaire.');
      return;
    }

    const request: ParcelleRequest = {
      nom: this.nom.trim(),
      superficie: this.superficie,
      typeSol: this.typeSol.trim() || null,
      typeIrrigation: this.typeIrrigation.trim() || null,
      description: this.description.trim() || null,
      exploitationId: this.exploitationId,
    };

    let operation = this.parcelleService.creer(request);

    if (this.id !== null) {
      operation = this.parcelleService.modifier(this.id, request);
    }

    this.enregistrement.set(true);

    operation.subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.router.navigate(['/parcelles']);
      },

      error: (erreur: unknown) => {
        this.enregistrement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  private afficherErreur(erreur: unknown): void {
    if (erreur instanceof HttpErrorResponse) {
      if (erreur.status === 401) {
        this.erreur.set('Session expirée. Connecte-toi à nouveau.');
      } else if (erreur.status === 403) {
        this.erreur.set('Tu ne disposes pas des permissions nécessaires.');
      } else if (erreur.status === 404) {
        this.erreur.set('La parcelle ou l’exploitation demandée n’existe plus.');
      } else if (erreur.status === 400) {
        this.erreur.set('Les informations envoyées sont invalides.');
      } else if (erreur.status === 409) {
        this.erreur.set(
          typeof erreur.error?.message === 'string'
            ? erreur.error.message
            : 'Cette modification est incompatible avec les données existantes.',
        );
      } else if (erreur.status === 0) {
        this.erreur.set('Impossible de joindre le backend.');
      } else {
        this.erreur.set('Impossible de terminer l’opération.');
      }
    } else {
      this.erreur.set('Vérifie ta session et réessaie.');
    }
  }
}
