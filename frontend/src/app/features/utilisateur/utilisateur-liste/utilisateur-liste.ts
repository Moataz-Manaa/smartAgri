import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

import { UtilisateurResponse } from '../../../core/models/utilisateur';
import { UtilisateurService } from '../../../core/services/utilisateur';
import { RouterLink } from '@angular/router';
import { Pagination } from '../../../shared/components/pagination/pagination';

@Component({
  selector: 'app-utilisateur-liste',
  imports: [RouterLink, Pagination],
  templateUrl: './utilisateur-liste.html',
  styleUrl: './utilisateur-liste.css',
})
export class UtilisateurListe implements OnInit {
  private utilisateurService = inject(UtilisateurService);

  utilisateurs = signal<UtilisateurResponse[]>([]);
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.chargerUtilisateurs(0);
  }

  chargement = signal(false);

  utilisateurEnCours = signal<number | null>(null);

  erreur = signal('');

  message = signal('');

  ngOnInit(): void {
    this.chargerUtilisateurs();
  }

  chargerUtilisateurs(page: number = this.page()): void {
    if (this.chargement() && this.totalPages() > 0) return;
    if (this.utilisateurEnCours() !== null) {
      return;
    }

    this.chargement.set(true);
    this.erreur.set('');
    this.message.set('');
    this.utilisateurs.set([]);

    this.utilisateurService.consulterPage(page, this.taille()).subscribe({
      next: (donnees) => {
        this.chargement.set(false);
        if (page > 0 && donnees.contenu.length === 0) {
          this.chargerUtilisateurs(Math.max(0, donnees.totalPages - 1));
          return;
        }
        this.utilisateurs.set(donnees.contenu);
        this.page.set(donnees.page);
        this.totalElements.set(donnees.totalElements);
        this.totalPages.set(donnees.totalPages);
        this.chargement.set(false);
      },

      error: (erreur: unknown) => {
        this.chargement.set(false);
        this.afficherErreur(erreur);
      },
    });
  }

  changerEtat(utilisateur: UtilisateurResponse): void {
    if (this.chargement() || this.utilisateurEnCours() !== null) {
      return;
    }

    this.erreur.set('');
    this.message.set('');
    this.utilisateurEnCours.set(utilisateur.id);

    let operation = this.utilisateurService.activer(utilisateur.id);

    if (utilisateur.actif) {
      operation = this.utilisateurService.desactiver(utilisateur.id);
    }

    operation.subscribe({
      next: (utilisateurModifie) => {
        const nouvelleListe: UtilisateurResponse[] = [];

        for (const element of this.utilisateurs()) {
          if (element.id === utilisateurModifie.id) {
            nouvelleListe.push(utilisateurModifie);
          } else {
            nouvelleListe.push(element);
          }
        }

        this.utilisateurs.set(nouvelleListe);
        this.utilisateurEnCours.set(null);

        if (utilisateurModifie.actif) {
          this.message.set('Le compte a été activé.');
        } else {
          this.message.set('Le compte a été désactivé dans Keycloak.');
        }
      },

      error: (erreur: unknown) => {
        this.utilisateurEnCours.set(null);
        this.afficherErreur(erreur);
      },
    });
  }

  private afficherErreur(erreur: unknown): void {
    if (erreur instanceof HttpErrorResponse) {
      if (erreur.status === 401) {
        this.erreur.set('Session invalide. Connecte-toi à nouveau.');
      } else if (erreur.status === 403) {
        this.erreur.set('Tu ne disposes pas des permissions nécessaires.');
      } else if (erreur.status === 404) {
        this.erreur.set('Un profil ou un compte Keycloak est introuvable.');
      } else if (erreur.status === 409) {
        this.erreur.set('Les données du compte sont incohérentes. Vérifie ses rôles.');
      } else if (erreur.status === 0) {
        this.erreur.set('Impossible de joindre le backend.');
      } else {
        this.erreur.set('Opération non confirmée. Actualise pour vérifier l’état des comptes.');
      }
    } else {
      this.erreur.set('Vérifie ta session et réessaie.');
    }
  }
}
