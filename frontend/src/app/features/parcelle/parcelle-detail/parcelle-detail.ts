import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ParcelleResponse } from '../../../core/models/parcelle';
import { ParcelleService } from '../../../core/services/parcelle';
import { PlantationListe } from '../../plantation/plantation-liste/plantation-liste';

@Component({
  selector: 'app-parcelle-detail',
  imports: [RouterLink, PlantationListe],
  templateUrl: './parcelle-detail.html',
})
export class ParcelleDetail implements OnInit {
  private route = inject(ActivatedRoute);
  private service = inject(ParcelleService);

  parcelle = signal<ParcelleResponse | null>(null);
  chargement = signal(true);
  erreur = signal('');

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.erreur.set('Identifiant incorrect.');
      this.chargement.set(false);
      return;
    }
    this.service.consulterParId(id).subscribe({
      next: (donnees) => {
        this.parcelle.set(donnees);

        this.chargement.set(false);
      },
      error: (erreur: unknown) => {
        this.chargement.set(false);
        if (erreur instanceof HttpErrorResponse && erreur.status === 403) {
          this.erreur.set('Vous ne disposez pas des droits pour consulter ces informations.');
        } else if (erreur instanceof HttpErrorResponse && erreur.status === 404) {
          this.erreur.set('Cette parcelle est introuvable.');
        } else {
          this.erreur.set(
            'Impossible de charger les informations. Vérifiez votre connexion et votre session.',
          );
        }
      },
    });
  }
}
