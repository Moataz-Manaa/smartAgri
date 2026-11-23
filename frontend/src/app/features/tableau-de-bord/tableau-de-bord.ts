import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { TableauDeBordService } from '../../core/services/tableau-de-bord';
import { ParcelleService } from '../../core/services/parcelle';
import { CampagneService } from '../../core/services/campagne';
import { AuthService } from '../../core/services/auth';
import { ParcelleResponse } from '../../core/models/parcelle';
import { CampagneResponse } from '../../core/models/campagne';
import {
  ActiviteRecenteResponse,
  AlertesTableauDeBordResponse,
  SyntheseResponse,
  TableauDeBordFiltres,
} from '../../core/models/tableau-de-bord';
import { TypeActivite } from '../../core/models/historique';

@Component({
  selector: 'app-tableau-de-bord',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './tableau-de-bord.html',
  styleUrl: './tableau-de-bord.css',
})
export class TableauDeBord implements OnInit, OnDestroy {
  auth = inject(AuthService);
  private service = inject(TableauDeBordService);
  private parcelleService = inject(ParcelleService);
  private campagneService = inject(CampagneService);
  private requetes = new Subscription();
  private options = new Subscription();
  private accesBloque = false;
  synthese = signal<SyntheseResponse | null>(null);
  activites = signal<ActiviteRecenteResponse[]>([]);
  alertes = signal<AlertesTableauDeBordResponse | null>(null);
  parcelles = signal<ParcelleResponse[]>([]);
  campagnes = signal<CampagneResponse[]>([]);
  chargementSynthese = signal(false);
  chargementActivites = signal(false);
  chargementAlertes = signal(false);
  erreurSynthese = signal('');
  erreurActivites = signal('');
  erreurAlertes = signal('');
  erreurParcelles = signal('');
  erreurCampagnes = signal('');
  erreurFiltre = signal('');
  erreurAcces = signal('');
  filtres = this.filtresInitiaux();
  filtresAppliques = { ...this.filtres };
  libelles: Record<TypeActivite, string> = {
    OBSERVATION: 'Observation',
    IRRIGATION: 'Irrigation',
    FERTILISATION: 'Fertilisation',
    TRAITEMENT: 'Traitement',
    RECOLTE: 'Récolte',
  };

  ngOnInit(): void {
    this.appliquer();
    if (!this.accesBloque) this.chargerOptions();
  }

  filtresInitiaux(): TableauDeBordFiltres {
    const fin = new Date();
    const debut = new Date(fin);
    debut.setUTCDate(debut.getUTCDate() - 29);
    return {
      parcelleId: null,
      campagneId: null,
      dateDebut: debut.toISOString().slice(0, 10),
      dateFin: fin.toISOString().slice(0, 10),
    };
  }

  appliquer(): void {
    this.requetes.unsubscribe();
    this.requetes = new Subscription();
    this.accesBloque = false;
    this.vider();
    this.erreurFiltre.set('');
    this.erreurAcces.set('');
    this.erreurSynthese.set('');
    this.erreurActivites.set('');
    this.erreurAlertes.set('');
    const f = this.filtres;
    if (
      !this.dateValide(f.dateDebut) ||
      !this.dateValide(f.dateFin) ||
      f.dateDebut > f.dateFin ||
      !this.idValide(f.parcelleId) ||
      !this.idValide(f.campagneId)
    ) {
      this.erreurFiltre.set(
        'Vérifiez les dates et les identifiants. La date de début doit précéder ou égaler la date de fin.',
      );
      return;
    }
    this.filtresAppliques = { ...f };
    this.chargementSynthese.set(true);
    this.requetes.add(
      this.service.consulterSynthese(this.filtresAppliques).subscribe({
        next: (resultat) => {
          this.synthese.set(resultat);
          this.chargementSynthese.set(false);
        },
        error: (e: HttpErrorResponse) => {
          this.chargementSynthese.set(false);
          this.traiterErreur(e, this.erreurSynthese);
        },
      }),
    );
    if (this.accesBloque) return;
    this.chargementActivites.set(true);
    this.requetes.add(
      this.service.consulterActivites(this.filtresAppliques).subscribe({
        next: (resultat) => {
          this.activites.set(resultat);
          this.chargementActivites.set(false);
        },
        error: (e: HttpErrorResponse) => {
          this.chargementActivites.set(false);
          this.traiterErreur(e, this.erreurActivites);
        },
      }),
    );
    if (this.accesBloque) return;
    this.chargementAlertes.set(true);
    this.requetes.add(
      this.service.consulterAlertes(this.filtresAppliques).subscribe({
        next: (resultat) => {
          this.alertes.set(resultat);
          this.chargementAlertes.set(false);
        },
        error: (e: HttpErrorResponse) => {
          this.chargementAlertes.set(false);
          this.traiterErreur(e, this.erreurAlertes);
        },
      }),
    );
  }

  reinitialiser(): void {
    this.filtres = this.filtresInitiaux();
    this.appliquer();
  }

  private chargerOptions(): void {
    this.options.add(
      this.parcelleService.consulterToutes().subscribe({
        next: (valeurs) => this.parcelles.set(valeurs),
        error: (e: HttpErrorResponse) => this.traiterErreur(e, this.erreurParcelles),
      }),
    );
    if (this.accesBloque) return;
    this.options.add(
      this.campagneService.consulterToutes().subscribe({
        next: (valeurs) => this.campagnes.set(valeurs),
        error: (e: HttpErrorResponse) => this.traiterErreur(e, this.erreurCampagnes),
      }),
    );
  }

  private idValide(id: number | null): boolean {
    return id === null || (Number.isSafeInteger(id) && id > 0);
  }

  private dateValide(date: string): boolean {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || date.startsWith('0000')) return false;
    const valeur = new Date(date + 'T00:00:00Z');
    return Number.isFinite(valeur.getTime()) && valeur.toISOString().slice(0, 10) === date;
  }

  private traiterErreur(erreur: HttpErrorResponse, message: { set(valeur: string): void }): void {
    if ([401, 403, 404].includes(erreur.status)) {
      this.accesBloque = true;
      this.requetes.unsubscribe();
      this.options.unsubscribe();
      this.vider();
      this.parcelles.set([]);
      this.campagnes.set([]);
      this.erreurAcces.set(
        erreur.status === 401
          ? 'Votre session est invalide. Connectez-vous à nouveau.'
          : 'Ce périmètre est introuvable ou vos droits ont changé. Rouvrez l’accueil pour vérifier vos accès.',
      );
    } else
      message.set('Impossible de charger ce bloc. Les autres informations restent consultables.');
  }

  private vider(): void {
    this.synthese.set(null);
    this.alertes.set(null);
    this.activites.set([]);
    this.chargementSynthese.set(false);
    this.chargementActivites.set(false);
    this.chargementAlertes.set(false);
  }

  ngOnDestroy(): void {
    this.requetes.unsubscribe();
    this.options.unsubscribe();
  }
}
