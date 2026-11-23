import { Component, Input, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { MeteoResponse, PrevisionMeteoResponse } from '../../../core/models/meteo';
import { MeteoService } from '../../../core/services/meteo';
import { AuthService } from '../../../core/services/auth';
import { Pagination } from '../../../shared/components/pagination/pagination';

@Component({
  selector: 'app-meteo-parcelle',
  imports: [DatePipe, DecimalPipe, RouterLink, Pagination],
  templateUrl: './meteo-parcelle.html',
  styleUrl: './meteo-parcelle.css',
})
export class MeteoParcelle implements OnChanges, OnDestroy {
  @Input() parcelleId = 0;
  private service = inject(MeteoService);
  private requete?: Subscription;
  authService = inject(AuthService);
  meteo = signal<MeteoResponse | null>(null);
  chargement = signal(false);
  erreur = signal('');
  page = signal(0);
  taille = signal(10);

  ngOnChanges(): void {
    this.charger();
  }

  charger(): void {
    this.requete?.unsubscribe();
    this.meteo.set(null);
    this.erreur.set('');
    this.page.set(0);
    this.chargement.set(false);
    if (!Number.isSafeInteger(this.parcelleId) || this.parcelleId <= 0) {
      this.erreur.set('Identifiant de parcelle invalide.');
      return;
    }
    this.chargement.set(true);
    this.requete = this.service.consulterParParcelle(this.parcelleId).subscribe({
      next: (resultat) => {
        this.meteo.set(resultat);
        this.chargement.set(false);
      },
      error: (erreur: HttpErrorResponse) => {
        this.chargement.set(false);
        if (erreur.status === 401)
          this.erreur.set('Votre session a expiré. Reconnectez-vous pour consulter la météo.');
        else if (erreur.status === 403)
          this.erreur.set('Vos droits ne permettent plus de consulter la météo de cette parcelle.');
        else if (erreur.status === 404) this.erreur.set('Cette parcelle est introuvable.');
        else
          this.erreur.set(
            'Impossible de charger la météo. Les autres informations de la parcelle restent accessibles.',
          );
      },
    });
  }

  donneesVisibles(): boolean {
    const meteo = this.meteo();
    return (
      meteo !== null &&
      meteo.donnees !== null &&
      (meteo.statut === 'DISPONIBLE' || meteo.statut === 'PERIMEE')
    );
  }

  previsions(): PrevisionMeteoResponse[] {
    if (!this.donneesVisibles()) return [];
    return this.meteo()!.donnees!.previsions;
  }

  previsionsPage(): PrevisionMeteoResponse[] {
    const debut = this.page() * this.taille();
    return this.previsions().slice(debut, debut + this.taille());
  }

  totalPages(): number {
    return Math.ceil(this.previsions().length / this.taille());
  }

  changerPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) this.page.set(page);
  }

  changerTaille(taille: number): void {
    if (![10, 20, 50].includes(taille)) return;
    this.taille.set(taille);
    this.page.set(0);
  }

  // DatePipe accepte un décalage comme +0100. Toutes les dates gardent la même référence horaire.
  decalageHoraire(): string {
    const secondes = this.meteo()?.donnees?.decalageUtcSecondes ?? 0;
    const minutes = Math.floor(Math.abs(secondes) / 60);
    return (
      (secondes < 0 ? '-' : '+') +
      String(Math.floor(minutes / 60)).padStart(2, '0') +
      String(minutes % 60).padStart(2, '0')
    );
  }

  libelleHoraire(): string {
    const decalage = this.decalageHoraire();
    return 'UTC' + decalage.slice(0, 3) + ':' + decalage.slice(3);
  }

  ngOnDestroy(): void {
    this.requete?.unsubscribe();
  }
}
