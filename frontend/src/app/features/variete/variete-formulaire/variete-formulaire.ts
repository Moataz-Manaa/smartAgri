import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { CultureResponse } from '../../../core/models/culture';
import { CultureService } from '../../../core/services/culture';
import { VarieteRequest } from '../../../core/models/variete';
import { VarieteService } from '../../../core/services/variete';

@Component({
  selector: 'app-variete-formulaire',
  imports: [FormsModule, RouterLink],
  templateUrl: './variete-formulaire.html',
  styleUrl: './variete-formulaire.css',
})
export class VarieteFormulaire implements OnInit {
  private service = inject(VarieteService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private cultureService = inject(CultureService);
  cultures = signal<CultureResponse[]>([]);
  cultureId: number | null = null;
  id: number | null = null;
  nom = '';
  description = '';
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
      cultures: this.cultureService.consulterToutes(),
      variete: this.id === null ? of(null) : this.service.consulterParId(this.id),
    }).subscribe({
      next: (donnees) => {
        this.cultures.set(donnees.cultures);
        if (donnees.variete !== null) {
          this.nom = donnees.variete.nom;
          this.description = donnees.variete.description ?? '';
          this.cultureId = donnees.variete.cultureId;
        } else {
          const cultureId = Number(this.route.snapshot.queryParamMap.get('cultureId'));
          for (const culture of donnees.cultures) {
            if (culture.id === cultureId) this.cultureId = cultureId;
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

  enregistrer(formulaire: NgForm): void {
    if (this.chargement() || this.enregistrement() || this.echecChargement()) return;
    this.erreur.set('');
    if (formulaire.invalid || this.nom.trim() === '' || this.cultureId === null) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Vérifiez les champs du formulaire.');
      return;
    }
    const request: VarieteRequest = {
      nom: this.nom.trim(),
      description: this.description.trim() || null,
      cultureId: this.cultureId,
    };
    const operation =
      this.id === null ? this.service.creer(request) : this.service.modifier(this.id, request);
    this.enregistrement.set(true);
    operation.subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.router.navigate(['/varietes'], { queryParams: { cultureId: this.cultureId } });
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
          : 'Une variété de ce nom existe déjà pour cette culture.',
      );
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
      this.erreur.set('La modification du catalogue est réservée à l’administrateur.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
      this.erreur.set('Session invalide. Connectez-vous à nouveau.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 404) {
      this.erreur.set('La variété ou la culture sélectionnée est introuvable.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 400) {
      this.erreur.set('Les informations envoyées sont invalides.');
    } else {
      this.erreur.set('Impossible de terminer l’opération. Vérifiez votre connexion et réessayez.');
    }
  }
}
