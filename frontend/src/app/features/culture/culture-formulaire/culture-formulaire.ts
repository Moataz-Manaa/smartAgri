import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CultureRequest } from '../../../core/models/culture';
import { CultureService } from '../../../core/services/culture';

@Component({
  selector: 'app-culture-formulaire',
  imports: [FormsModule, RouterLink],
  templateUrl: './culture-formulaire.html',
  styleUrl: './culture-formulaire.css',
})
export class CultureFormulaire implements OnInit {
  private service = inject(CultureService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  type = '';
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
    if (this.id === null) return;
    this.chargement.set(true);
    this.service.consulterParId(this.id).subscribe({
      next: (culture) => {
        this.nom = culture.nom;
        this.description = culture.description ?? '';
        this.type = culture.type ?? '';
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
    if (formulaire.invalid || this.nom.trim() === '') {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Vérifiez les champs du formulaire.');
      return;
    }
    const request: CultureRequest = {
      nom: this.nom.trim(),
      description: this.description.trim() || null,
      type: this.type.trim() || null,
    };
    const operation =
      this.id === null ? this.service.creer(request) : this.service.modifier(this.id, request);
    this.enregistrement.set(true);
    operation.subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.router.navigate(['/cultures']);
      },
      error: (erreur: unknown) => {
        this.enregistrement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  private afficherErreur(erreur: unknown): void {
    if (erreur instanceof HttpErrorResponse && erreur.status === 409) {
      this.erreur.set('Une culture de ce nom existe déjà.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
      this.erreur.set('La modification du catalogue est réservée à l’administrateur.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
      this.erreur.set('Session invalide. Connectez-vous à nouveau.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 404) {
      this.erreur.set('Cette culture est introuvable.');
    } else if (erreur instanceof HttpErrorResponse && erreur.status === 400) {
      this.erreur.set('Les informations envoyées sont invalides.');
    } else {
      this.erreur.set('Impossible de terminer l’opération. Vérifiez votre connexion et réessayez.');
    }
  }
}
