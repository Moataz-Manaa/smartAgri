import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import {
  RoleUtilisateur,
  UtilisateurModificationRequest,
  UtilisateurResponse,
} from '../../../core/models/utilisateur';
import { UtilisateurService } from '../../../core/services/utilisateur';

@Component({
  selector: 'app-utilisateur-formulaire',
  imports: [FormsModule, RouterLink],
  templateUrl: './utilisateur-formulaire.html',
  styleUrl: './utilisateur-formulaire.css',
})
export class UtilisateurFormulaire implements OnInit {
  private utilisateurService = inject(UtilisateurService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  id: number | null = null;

  username = '';
  nom = '';
  prenom = '';
  email = '';
  motDePasse = '';
  role: RoleUtilisateur | null = null;

  chargement = signal(false);
  echecChargement = signal(false);
  enregistrement = signal(false);
  erreur = signal('');

  ngOnInit(): void {
    const idDansUrl = this.route.snapshot.paramMap.get('id');

    if (idDansUrl === null) {
      return;
    }

    const id = Number(idDansUrl);

    if (!Number.isSafeInteger(id) || id <= 0) {
      this.echecChargement.set(true);
      this.erreur.set('L’identifiant utilisateur est invalide.');
      return;
    }

    this.id = id;
    this.chargement.set(true);

    this.utilisateurService.consulterParId(id).subscribe({
      next: (utilisateur) => {
        this.username = utilisateur.username;
        this.nom = utilisateur.nom;
        this.prenom = utilisateur.prenom;
        this.email = utilisateur.email;
        this.role = utilisateur.role;

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
    return this.chargement() || this.echecChargement() || this.enregistrement();
  }

  enregistrer(formulaire: NgForm): void {
    if (this.formulaireBloque()) {
      return;
    }

    this.erreur.set('');

    if (
      formulaire.invalid ||
      this.nom.trim() === '' ||
      this.prenom.trim() === '' ||
      this.role === null
    ) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('Vérifie les champs du formulaire.');
      return;
    }

    if (this.id === null && (this.username.trim() === '' || this.motDePasse.trim() === '')) {
      formulaire.control.markAllAsTouched();
      this.erreur.set('L’identifiant et le mot de passe sont obligatoires.');
      return;
    }

    const profil: UtilisateurModificationRequest = {
      nom: this.nom.trim(),
      prenom: this.prenom.trim(),
      email: this.email.trim(),
      role: this.role,
    };

    let operation: Observable<UtilisateurResponse>;

    if (this.id === null) {
      operation = this.utilisateurService.creer({
        username: this.username.trim(),
        motDePasse: this.motDePasse,
        nom: profil.nom,
        prenom: profil.prenom,
        email: profil.email,
        role: profil.role,
      });
    } else {
      operation = this.utilisateurService.modifier(this.id, profil);
    }

    this.enregistrement.set(true);

    operation.subscribe({
      next: () => {
        this.motDePasse = '';
        this.enregistrement.set(false);
        this.router.navigate(['/utilisateurs']);
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
        this.erreur.set('Le profil ou le compte Keycloak est introuvable.');
      } else if (erreur.status === 409) {
        this.erreur.set('Conflit : vérifie l’email, l’identifiant et les rôles du compte.');
      } else if (erreur.status === 400) {
        this.erreur.set('Les informations envoyées sont invalides.');
      } else if (erreur.status === 503 || erreur.status === 0) {
        this.erreur.set(
          'Opération non confirmée. Vérifie la liste et Keycloak avant de réessayer.',
        );
      } else {
        this.erreur.set('Impossible de terminer l’opération.');
      }
    } else {
      this.erreur.set('Vérifie ta session et réessaie.');
    }
  }
}
