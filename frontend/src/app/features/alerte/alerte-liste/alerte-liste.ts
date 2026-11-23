import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import {
  AlerteFiltres,
  AlerteResponse,
  CompteurAlertesResponse,
  TypeAlerte,
  NiveauAlerte,
  StatutAlerte,
} from '../../../core/models/alerte';
import { ParcelleResponse } from '../../../core/models/parcelle';
import { AlerteService } from '../../../core/services/alerte';
import { ParcelleService } from '../../../core/services/parcelle';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { AlerteBadges } from '../../../shared/ui/alerte-badges/alerte-badges';
import { AlerteLibellePipe } from '../../../shared/pipes/alerte-libelle';
import { messageErreurAlerte } from '../alerte-erreur';

@Component({
  selector: 'app-alerte-liste',
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    Pagination,
    AlerteBadges,
    AlerteLibellePipe,
  ],
  templateUrl: './alerte-liste.html',
  styleUrl: './alerte-liste.css',
})
export class AlerteListe implements OnInit, OnDestroy {
  private service = inject(AlerteService);
  private parcelleService = inject(ParcelleService);
  private route = inject(ActivatedRoute);
  private requete?: Subscription;
  private requeteCompteur?: Subscription;
  private requeteParcelles?: Subscription;
  private navigation?: Subscription;
  private accesBloque = false;
  alertes = signal<AlerteResponse[]>([]);
  parcelles = signal<ParcelleResponse[]>([]);
  compteur = signal<CompteurAlertesResponse | null>(null);
  chargement = signal(false);
  chargementCompteur = signal(false);
  erreur = signal('');
  erreurCompteur = signal('');
  erreurParcelles = signal('');
  erreurFiltre = signal('');
  page = signal(0);
  taille = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);
  filtres: AlerteFiltres = this.filtresInitiaux();
  filtresAppliques: AlerteFiltres = this.filtresInitiaux();

  ngOnInit(): void {
    this.navigation = this.route.queryParamMap.subscribe((params) => this.lireParametres(params));
    this.chargerParcelles();
  }

  private lireParametres(params: ParamMap): void {
    this.requete?.unsubscribe();
    this.requeteCompteur?.unsubscribe();
    this.vider();
    this.compteur.set(null);
    this.chargement.set(false);
    this.chargementCompteur.set(false);
    this.erreur.set('');
    this.erreurFiltre.set('');
    this.filtres = {
      parcelleId: params.has('parcelleId') ? Number(params.get('parcelleId')) : null,
      type: (params.get('type') ?? '') as TypeAlerte | '',
      niveau: (params.get('niveau') ?? '') as NiveauAlerte | '',
      statut: (params.get('statut') ?? 'ACTIVE') as StatutAlerte | '',
      lue: params.get('lue') === 'true' ? true : params.get('lue') === 'false' ? false : null,
      dateDebut: params.get('dateDebut') ?? '',
      dateFin: params.get('dateFin') ?? '',
    };
    const page = Number(params.get('page') ?? 0);
    const taille = Number(params.get('taille') ?? 10);
    if (
      !Number.isSafeInteger(page) ||
      page < 0 ||
      ![10, 20, 50].includes(taille) ||
      (params.has('lue') && !['true', 'false'].includes(params.get('lue')!))
    ) {
      this.erreurFiltre.set(
        'Les paramètres de la liste sont invalides. Réinitialisez les filtres.',
      );
      return;
    }
    this.taille.set(taille);
    this.appliquer(page);
  }

  filtresInitiaux(): AlerteFiltres {
    return {
      parcelleId: null,
      type: '',
      niveau: '',
      statut: 'ACTIVE',
      lue: null,
      dateDebut: '',
      dateFin: '',
    };
  }

  appliquer(page = 0): void {
    this.accesBloque = false;
    this.requete?.unsubscribe();
    this.requeteCompteur?.unsubscribe();
    this.vider();
    this.compteur.set(null);
    this.erreur.set('');
    this.erreurCompteur.set('');
    this.chargement.set(false);
    this.chargementCompteur.set(false);
    this.erreurFiltre.set('');
    if (!this.filtresValides()) {
      this.erreurFiltre.set(
        'Vérifiez les filtres : la parcelle et les dates doivent être valides, avec une date de début antérieure ou égale à la date de fin.',
      );
      return;
    }
    this.filtresAppliques = { ...this.filtres };
    this.chargerPage(page);
    if (!this.accesBloque) this.chargerCompteur();
  }

  private filtresValides(): boolean {
    const f = this.filtres;
    return (
      (f.parcelleId === null || (Number.isSafeInteger(f.parcelleId) && f.parcelleId > 0)) &&
      ['', 'CHALEUR', 'GEL', 'PLUIE_IMPORTANTE'].includes(f.type) &&
      ['', 'AVERTISSEMENT', 'CRITIQUE'].includes(f.niveau) &&
      ['', 'ACTIVE', 'LEVEE', 'EXPIREE'].includes(f.statut) &&
      this.dateValide(f.dateDebut) &&
      this.dateValide(f.dateFin) &&
      !(f.dateDebut && f.dateFin && f.dateDebut > f.dateFin)
    );
  }

  private dateValide(date: string): boolean {
    if (!date) return true;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || date.startsWith('0000')) return false;
    const valeur = new Date(date + 'T00:00:00Z');
    return Number.isFinite(valeur.getTime()) && valeur.toISOString().slice(0, 10) === date;
  }

  chargerPage(page: number): void {
    this.requete?.unsubscribe();
    this.vider();
    this.page.set(page);
    this.erreur.set('');
    this.chargement.set(true);
    this.requete = this.service
      .consulterPage(page, this.taille(), this.filtresAppliques)
      .subscribe({
        next: (resultat) => {
          if (page > 0 && resultat.contenu.length === 0 && resultat.totalPages <= page) {
            this.chargerPage(Math.max(0, resultat.totalPages - 1));
            return;
          }
          this.alertes.set(resultat.contenu);
          this.page.set(resultat.page);
          this.totalElements.set(resultat.totalElements);
          this.totalPages.set(resultat.totalPages);
          this.chargement.set(false);
        },
        error: (erreur: HttpErrorResponse) => {
          this.chargement.set(false);
          this.erreur.set(messageErreurAlerte(erreur));
          if ([401, 403, 404].includes(erreur.status)) this.retirerDonneesInterdites(erreur);
        },
      });
  }

  private chargerCompteur(): void {
    this.chargementCompteur.set(true);
    this.requeteCompteur = this.service
      .consulterCompteur(this.filtresAppliques.parcelleId)
      .subscribe({
        next: (resultat) => {
          this.compteur.set(resultat);
          this.chargementCompteur.set(false);
        },
        error: (erreur: HttpErrorResponse) => {
          this.chargementCompteur.set(false);
          this.erreurCompteur.set(messageErreurAlerte(erreur));
          if ([401, 403, 404].includes(erreur.status)) this.retirerDonneesInterdites(erreur);
        },
      });
  }

  private chargerParcelles(): void {
    this.requeteParcelles = this.parcelleService.consulterToutes().subscribe({
      next: (parcelles) => this.parcelles.set(parcelles),
      error: (erreur: HttpErrorResponse) => {
        if ([401, 403].includes(erreur.status)) this.retirerDonneesInterdites(erreur);
        else
          this.erreurParcelles.set(
            'Le filtre par parcelle est indisponible. Les autres filtres restent utilisables.',
          );
      },
    });
  }

  private retirerDonneesInterdites(erreur: HttpErrorResponse): void {
    this.accesBloque = true;
    this.requete?.unsubscribe();
    this.requeteCompteur?.unsubscribe();
    this.requeteParcelles?.unsubscribe();
    this.vider();
    this.parcelles.set([]);
    this.compteur.set(null);
    this.chargement.set(false);
    this.chargementCompteur.set(false);
    this.erreur.set(messageErreurAlerte(erreur));
  }

  private vider(): void {
    this.alertes.set([]);
    this.totalElements.set(0);
    this.totalPages.set(0);
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.chargerPage(0);
  }

  reinitialiser(): void {
    this.filtres = this.filtresInitiaux();
    this.appliquer();
  }

  afficherNonLues(): void {
    this.filtres = { ...this.filtresAppliques, statut: 'ACTIVE', lue: false };
    this.appliquer();
  }

  parametresRetour() {
    const f = this.filtresAppliques;
    return {
      parcelleId: f.parcelleId,
      type: f.type || null,
      niveau: f.niveau || null,
      statut: f.statut,
      lue: f.lue,
      dateDebut: f.dateDebut || null,
      dateFin: f.dateFin || null,
      page: this.page(),
      taille: this.taille(),
    };
  }

  ngOnDestroy(): void {
    this.navigation?.unsubscribe();
    this.requete?.unsubscribe();
    this.requeteCompteur?.unsubscribe();
    this.requeteParcelles?.unsubscribe();
  }
}
