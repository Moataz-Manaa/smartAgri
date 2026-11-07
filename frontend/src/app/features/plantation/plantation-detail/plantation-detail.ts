import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PlantationService } from '../../../core/services/plantation';
import { AuthService } from '../../../core/services/auth';
import { PlantationResponse } from '../../../core/models/plantation';
import { StatutPlantationPipe } from '../../../shared/pipes/statut-plantation';
import { ObservationListe } from '../../observation/observation-liste/observation-liste';
import { IrrigationListe } from '../../irrigation/irrigation-liste/irrigation-liste';
import { FertilisationListe } from '../../fertilisation/fertilisation-liste/fertilisation-liste';
import { TraitementListe } from '../../traitement/traitement-liste/traitement-liste';

@Component({
  selector: 'app-plantation-detail',
  imports: [
    RouterLink,
    DatePipe,
    StatutPlantationPipe,
    ObservationListe,
    IrrigationListe,
    FertilisationListe,
    TraitementListe,
  ],
  templateUrl: './plantation-detail.html',
})
export class PlantationDetail implements OnInit {
  private service = inject(PlantationService);
  private route = inject(ActivatedRoute);
  authService = inject(AuthService);
  plantation = signal<PlantationResponse | null>(null);
  chargement = signal(true);
  erreur = signal('');

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.chargement.set(false);
      this.erreur.set('Identifiant invalide.');
      return;
    }
    this.service.consulterParId(id).subscribe({
      next: (plantation) => {
        this.plantation.set(plantation);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set(
          'Impossible de consulter cette plantation. Vérifiez votre accès et votre connexion.',
        );
      },
    });
  }
}
