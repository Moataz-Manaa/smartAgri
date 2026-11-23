import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { TableauDeBord } from './tableau-de-bord';
import { TableauDeBordService } from '../../core/services/tableau-de-bord';
import { ParcelleService } from '../../core/services/parcelle';
import { CampagneService } from '../../core/services/campagne';
import { AuthService } from '../../core/services/auth';
import {
  ActiviteRecenteResponse,
  AlertesTableauDeBordResponse,
  SyntheseResponse,
} from '../../core/models/tableau-de-bord';

const synthese: SyntheseResponse = {
  dateDebut: '2026-11-01',
  dateFin: '2026-11-30',
  nombreParcelles: 2,
  nombreCampagnes: 1,
  nombrePlantations: 2,
  nombreActivites: 3,
  volumeIrrigationMetresCubes: 0.005,
  quantiteRecolteeKilogrammes: 1100,
};
const alertes: AlertesTableauDeBordResponse = {
  actives: 2,
  activesNonLues: 1,
  calculeLe: '2026-11-14T10:00:00Z',
};
const activite: ActiviteRecenteResponse = {
  id: 1,
  type: 'OBSERVATION',
  date: '2026-11-14',
  plantationId: 9,
  parcelleId: 11,
  parcelleCode: 'PAR-11',
  parcelleNom: 'Oliviers',
  campagneNom: 'Campagne 2026',
  description: 'Feuilles observées',
};

describe('Bilan agricole de l’accueil', () => {
  let role: string;
  let service: {
    consulterSynthese: ReturnType<typeof vi.fn>;
    consulterActivites: ReturnType<typeof vi.fn>;
    consulterAlertes: ReturnType<typeof vi.fn>;
  };
  let parcelles: { consulterToutes: ReturnType<typeof vi.fn> };
  let campagnes: { consulterToutes: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    role = 'AGRICULTEUR';
    service = {
      consulterSynthese: vi.fn(() => of(synthese)),
      consulterActivites: vi.fn(() => of([activite])),
      consulterAlertes: vi.fn(() => of(alertes)),
    };
    parcelles = { consulterToutes: vi.fn(() => of([{ id: 11, code: 'PAR-11', nom: 'Oliviers' }])) };
    campagnes = { consulterToutes: vi.fn(() => of([{ id: 3, nom: 'Campagne 2026' }])) };
    TestBed.configureTestingModule({
      imports: [TableauDeBord],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { possedeRole: (valeur: string) => valeur === role } },
        { provide: TableauDeBordService, useValue: service },
        { provide: ParcelleService, useValue: parcelles },
        { provide: CampagneService, useValue: campagnes },
      ],
    });
  });

  for (const metier of ['ADMIN', 'AGRICULTEUR', 'AGRONOME']) {
    it('affiche des indicateurs et liens de consultation pour ' + metier, async () => {
      role = metier;
      const fixture = TestBed.createComponent(TableauDeBord);
      await fixture.whenStable();
      const page: HTMLElement = fixture.nativeElement;
      expect(page.textContent).toContain(
        metier === 'ADMIN' ? 'Vue globale de la société' : 'affectations actuelles',
      );
      expect(page.querySelector('[data-testid="volume-bilan"]')?.textContent).toContain('0.005 m³');
      expect(page.querySelector('[data-testid="recolte-bilan"]')?.textContent).toContain(
        '1,100 kg',
      );
      expect(page.textContent).toContain('Dont 1 non lues par vous');
      expect(page.textContent).toContain('14/11/2026 à 10:00 UTC');
      expect(page.querySelector('a[href="/plantations/9"]')).not.toBeNull();
      expect(page.querySelector('a[href="/alertes"]')).not.toBeNull();
      expect(page.querySelector('a[href*="nouvelle"]')).toBeNull();
      expect(page.querySelector('a[href*="modifier"]')).toBeNull();
      expect(page.textContent).not.toContain('Actualiser');
      expect(service.consulterSynthese).toHaveBeenCalledTimes(1);
      expect(service.consulterActivites).toHaveBeenCalledTimes(1);
      expect(service.consulterAlertes).toHaveBeenCalledTimes(1);
    });
  }

  it('utilise une période UTC de trente journées et distingue le formulaire des filtres appliqués', async () => {
    const fixture = TestBed.createComponent(TableauDeBord);
    await fixture.whenStable();
    const c = fixture.componentInstance,
      f = c.filtresAppliques;
    expect(f.dateFin).toBe(new Date().toISOString().slice(0, 10));
    expect((Date.parse(f.dateFin) - Date.parse(f.dateDebut)) / 86400000).toBe(29);
    c.filtres.dateDebut = '2026-11-01';
    c.filtres.dateFin = '2026-11-30';
    c.filtres.parcelleId = 11;
    c.filtres.campagneId = 3;
    expect(c.filtresAppliques.parcelleId).toBeNull();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(service.consulterSynthese).toHaveBeenLastCalledWith({
      dateDebut: '2026-11-01',
      dateFin: '2026-11-30',
      parcelleId: 11,
      campagneId: 3,
    });
    expect(service.consulterAlertes).toHaveBeenLastCalledWith(c.filtresAppliques);
    expect(
      fixture.nativeElement.querySelector('[data-testid="periode-bilan"]').textContent,
    ).toContain('01/11/2026 au 30/11/2026');
    c.filtres.dateFin = '2026-12-31';
    expect(c.filtresAppliques.dateFin).toBe('2026-11-30');
    c.reinitialiser();
    expect(c.filtres.parcelleId).toBeNull();
    expect(c.filtres.campagneId).toBeNull();
  });

  it('refuse les dates et les identifiants invalides sans appeler les trois API', async () => {
    const fixture = TestBed.createComponent(TableauDeBord);
    await fixture.whenStable();
    for (const dates of [
      ['2026-11-30', '2026-11-01'],
      ['2026-02-30', '2026-11-30'],
      ['0000-01-01', '2026-11-30'],
      ['', '2026-11-30'],
    ]) {
      fixture.componentInstance.filtres.dateDebut = dates[0];
      fixture.componentInstance.filtres.dateFin = dates[1];
      fixture.componentInstance.appliquer();
      expect(fixture.componentInstance.erreurFiltre()).not.toBe('');
      expect(fixture.componentInstance.synthese()).toBeNull();
    }
    fixture.componentInstance.filtres = {
      dateDebut: '2026-11-01',
      dateFin: '2026-11-30',
      parcelleId: -1,
      campagneId: null,
    };
    fixture.componentInstance.appliquer();
    expect(service.consulterSynthese).toHaveBeenCalledTimes(1);
    expect(service.consulterActivites).toHaveBeenCalledTimes(1);
    expect(service.consulterAlertes).toHaveBeenCalledTimes(1);
  });

  for (const bloc of ['consulterSynthese', 'consulterActivites', 'consulterAlertes'] as const) {
    it('isole une panne du bloc ' + bloc + ' sans inventer de zéro', async () => {
      service[bloc].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
      const fixture = TestBed.createComponent(TableauDeBord);
      await fixture.whenStable();
      expect(fixture.componentInstance.erreurAcces()).toBe('');
      expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
        'Impossible de charger',
      );
      expect(fixture.componentInstance.synthese()).toEqual(
        bloc === 'consulterSynthese' ? null : synthese,
      );
      expect(fixture.componentInstance.alertes()).toEqual(
        bloc === 'consulterAlertes' ? null : alertes,
      );
      expect(fixture.componentInstance.activites()).toEqual(
        bloc === 'consulterActivites' ? [] : [activite],
      );
    });
  }

  it('signale un résultat vide sans conclure à une absence de risque', async () => {
    service.consulterSynthese.mockReturnValue(
      of({
        ...synthese,
        nombreActivites: 0,
        volumeIrrigationMetresCubes: 0,
        quantiteRecolteeKilogrammes: 0,
      }),
    );
    service.consulterActivites.mockReturnValue(of([]));
    service.consulterAlertes.mockReturnValue(of({ ...alertes, actives: 0, activesNonLues: 0 }));
    const fixture = TestBed.createComponent(TableauDeBord);
    await fixture.whenStable();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.textContent).toContain('Aucune activité enregistrée');
    expect(page.textContent).toContain("ne garantit pas l'absence de risque");
    expect(page.querySelector('[data-testid="volume-bilan"]')?.textContent).toContain('0 m³');
  });

  it('garde les autres filtres utilisables si les listes de choix sont indisponibles', async () => {
    parcelles.consulterToutes.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    campagnes.consulterToutes.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    const fixture = TestBed.createComponent(TableauDeBord);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#bilan-parcelle').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('#bilan-campagne').disabled).toBe(true);
    expect(fixture.componentInstance.synthese()).toEqual(synthese);
    fixture.componentInstance.appliquer();
    expect(service.consulterSynthese).toHaveBeenCalledTimes(2);
  });

  for (const statut of [401, 403, 404]) {
    it(
      'efface tous les blocs après un refus ' + statut + ' et annule les réponses tardives',
      async () => {
        const reponse = new Subject<AlertesTableauDeBordResponse>();
        const options = new Subject<never[]>();
        service.consulterAlertes.mockReturnValue(reponse);
        campagnes.consulterToutes.mockReturnValue(options);
        const fixture = TestBed.createComponent(TableauDeBord);
        await fixture.whenStable();
        expect(fixture.componentInstance.synthese()).toEqual(synthese);
        reponse.error(new HttpErrorResponse({ status: statut }));
        await fixture.whenStable();
        expect(fixture.componentInstance.synthese()).toBeNull();
        expect(fixture.componentInstance.activites()).toEqual([]);
        expect(fixture.componentInstance.parcelles()).toEqual([]);
        expect(fixture.componentInstance.alertes()).toBeNull();
        expect(options.observed).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="synthese-bilan"]')).toBeNull();
        expect(fixture.componentInstance.erreurAcces()).not.toBe('');
      },
    );
  }

  it('arrête les autres appels après un refus synchrone du premier bloc', async () => {
    service.consulterSynthese.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    const fixture = TestBed.createComponent(TableauDeBord);
    await fixture.whenStable();
    expect(service.consulterActivites).not.toHaveBeenCalled();
    expect(service.consulterAlertes).not.toHaveBeenCalled();
    expect(parcelles.consulterToutes).not.toHaveBeenCalled();
  });

  it('annule les anciennes requêtes au changement de filtre et à la destruction', async () => {
    const ancien = new Subject<SyntheseResponse>();
    service.consulterSynthese.mockReturnValue(ancien);
    const autres = new Subject<ActiviteRecenteResponse[]>();
    service.consulterActivites.mockReturnValue(autres);
    const fixture = TestBed.createComponent(TableauDeBord);
    await fixture.whenStable();
    expect(ancien.observed).toBe(true);
    const nouveau = new Subject<SyntheseResponse>();
    service.consulterSynthese.mockReturnValue(nouveau);
    fixture.componentInstance.filtres.parcelleId = 11;
    fixture.componentInstance.appliquer();
    expect(ancien.observed).toBe(false);
    ancien.next(synthese);
    expect(fixture.componentInstance.synthese()).toBeNull();
    fixture.destroy();
    expect(nouveau.observed).toBe(false);
    expect(autres.observed).toBe(false);
  });
});

describe('API Angular du tableau de bord', () => {
  it('transmet les mêmes filtres aux trois routes sans rôle ni identifiant de compte', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(TableauDeBordService),
      http = TestBed.inject(HttpTestingController);
    const filtres = {
      parcelleId: 11,
      campagneId: 3,
      dateDebut: '2026-11-01',
      dateFin: '2026-11-30',
    };
    service.consulterSynthese(filtres).subscribe();
    service.consulterActivites(filtres).subscribe();
    service.consulterAlertes(filtres).subscribe();
    for (const bloc of ['synthese', 'activites', 'alertes']) {
      const r = http.expectOne(
        (req) => req.url === 'http://localhost:8081/api/tableau-de-bord/' + bloc,
      );
      expect(r.request.method).toBe('GET');
      expect(r.request.params.keys().sort()).toEqual([
        'campagneId',
        'dateDebut',
        'dateFin',
        'parcelleId',
      ]);
      expect(r.request.params.get('parcelleId')).toBe('11');
      expect(r.request.params.get('campagneId')).toBe('3');
      expect(r.request.params.get('dateDebut')).toBe('2026-11-01');
      expect(r.request.params.get('dateFin')).toBe('2026-11-30');
      r.flush(bloc === 'activites' ? [] : bloc === 'alertes' ? alertes : synthese);
    }
    http.verify();
  });
});
