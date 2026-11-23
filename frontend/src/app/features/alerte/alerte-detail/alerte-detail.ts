import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AlerteResponse } from '../../../core/models/alerte';
import { AlerteService } from '../../../core/services/alerte';
import { AlerteBadges } from '../../../shared/ui/alerte-badges/alerte-badges';
import { AlerteLibellePipe } from '../../../shared/pipes/alerte-libelle';
import { messageErreurAlerte } from '../alerte-erreur';

@Component({
  selector: 'app-alerte-detail',
  imports: [RouterLink, DatePipe, DecimalPipe, AlerteBadges, AlerteLibellePipe],
  templateUrl: './alerte-detail.html',
  host: { class: 'block min-w-0' },
})
export class AlerteDetail implements OnInit, OnDestroy {
  private service = inject(AlerteService);
  private route = inject(ActivatedRoute);
  private navigation?: Subscription;
  private requete?: Subscription;
  private lecture?: Subscription;
  retour = this.route.snapshot.queryParams;
  alerte = signal<AlerteResponse | null>(null);
  chargement = signal(false);
  enregistrement = signal(false);
  erreur = signal('');
  erreurLecture = signal('');
  confirmation = signal('');

  ngOnInit(): void {
    this.navigation = this.route.paramMap.subscribe((params) =>
      this.charger(Number(params.get('id'))),
    );
  }

  private charger(id: number): void {
    this.requete?.unsubscribe();
    this.lecture?.unsubscribe();
    this.alerte.set(null);
    this.erreur.set('');
    this.erreurLecture.set('');
    this.confirmation.set('');
    this.enregistrement.set(false);
    this.chargement.set(false);
    this.retour = this.route.snapshot.queryParams;
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.erreur.set('Identifiant d’alerte invalide.');
      return;
    }
    this.chargement.set(true);
    this.requete = this.service.consulterParId(id).subscribe({
      next: (resultat) => {
        this.alerte.set(resultat);
        this.chargement.set(false);
      },
      error: (erreur: HttpErrorResponse) => {
        this.erreur.set(messageErreurAlerte(erreur));
        this.chargement.set(false);
      },
    });
  }

  marquerLue(): void {
    const alerte = this.alerte();
    if (!alerte || alerte.lue || this.enregistrement()) return;
    this.enregistrement.set(true);
    this.erreurLecture.set('');
    this.lecture = this.service.marquerLue(alerte.id).subscribe({
      next: (resultat) => {
        this.alerte.set(resultat);
        this.enregistrement.set(false);
        this.confirmation.set('Lecture enregistrée pour votre compte.');
      },
      error: (erreur: HttpErrorResponse) => {
        this.enregistrement.set(false);
        if ([401, 403, 404].includes(erreur.status)) {
          this.alerte.set(null);
          this.erreur.set(messageErreurAlerte(erreur));
        } else
          this.erreurLecture.set(
            'Impossible de confirmer la lecture. Réessayez lorsque la connexion sera rétablie.',
          );
      },
    });
  }

  ngOnDestroy(): void {
    this.navigation?.unsubscribe();
    this.requete?.unsubscribe();
    this.lecture?.unsubscribe();
  }
}
