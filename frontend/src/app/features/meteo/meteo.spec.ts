import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { MeteoParcelle } from './meteo-parcelle/meteo-parcelle';
import { MeteoService } from '../../core/services/meteo';
import { MeteoResponse } from '../../core/models/meteo';
import { AuthService } from '../../core/services/auth';
import { ParcelleService } from '../../core/services/parcelle';
import { PlantationService } from '../../core/services/plantation';
import { CampagneService } from '../../core/services/campagne';
import { routes } from '../../app.routes';

describe('Météo des parcelles', () => {
  let role: string;
  let meteo: MeteoResponse;
  let service: { consulterParParcelle: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    role = 'ADMIN';
    meteo = {
      parcelleId: 11,
      exploitationId: 7,
      exploitationNom: 'Ferme Sfax',
      latitude: 0,
      longitude: 0,
      statut: 'DISPONIBLE',
      message: 'Données disponibles',
      source: 'OpenWeather',
      lienSource: 'https://openweathermap.org/',
      donnees: {
        recupereesLe: '2026-11-12T12:10:00Z',
        decalageUtcSecondes: 3600,
        conditions: {
          dateDonnees: '2026-11-12T12:00:00Z',
          temperatureCelsius: 0,
          humiditePourcent: 0,
          pluieDerniereHeureMillimetres: 0,
          description: 'Ciel clair',
        },
        previsions: [],
      },
    };
    for (let i = 1; i <= 40; i++) {
      meteo.donnees!.previsions.push({
        datePrevision: new Date(Date.parse('2026-11-12T12:00:00Z') + i * 3 * 3600000).toISOString(),
        temperatureCelsius: 22,
        humiditePourcent: 60,
        probabilitePrecipitationsPourcent: 70,
        pluieTroisHeuresMillimetres: 1.2,
        description: 'Pluie légère',
      });
    }
    service = { consulterParParcelle: vi.fn(() => of(meteo)) };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: MeteoService, useValue: service },
        {
          provide: AuthService,
          useValue: { estConnecte: () => true, possedeRole: (r: string) => r === role },
        },
      ],
    });
  });

  async function ouvrir() {
    const fixture = TestBed.createComponent(MeteoParcelle);
    fixture.componentRef.setInput('parcelleId', 11);
    await fixture.whenStable();
    return fixture;
  }

  function texte(element: HTMLElement): string {
    return element.textContent!.replace(/\s+/g, ' ');
  }

  it('affiche les unités, le point météo et les valeurs égales à zéro', async () => {
    const fixture = await ouvrir();
    const page = texte(fixture.nativeElement);
    expect(page).toContain('0 °C');
    expect(page).toContain('0 %');
    expect(page).toContain('0 mm');
    expect(page).toContain('Ferme Sfax');
    expect(page).toContain('Point de référence : 0, 0');
    expect(page).toContain('70 %');
    expect(page).toContain('Pluie sur 3 heures');
    expect(
      fixture.nativeElement.querySelector('a[href="https://openweathermap.org/"]'),
    ).not.toBeNull();
    expect(fixture.nativeElement.querySelector('img[alt="OpenWeather"]')).not.toBeNull();
  });

  it('affiche toutes les dates avec le décalage indiqué par le fournisseur', async () => {
    const fixture = await ouvrir();
    expect(texte(fixture.nativeElement)).toContain('12/11/2026 à 13:00');
    expect(texte(fixture.nativeElement)).toContain('UTC+01:00');
    expect(texte(fixture.nativeElement)).toContain('12/11/2026 — 16:00');
    meteo.donnees!.decalageUtcSecondes = -19800;
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(texte(fixture.nativeElement)).toContain('UTC-05:30');
    expect(texte(fixture.nativeElement)).toContain('12/11/2026 à 06:30');
  });

  it('présente les valeurs absentes comme non disponibles', async () => {
    meteo.donnees!.conditions.temperatureCelsius = null;
    meteo.donnees!.conditions.humiditePourcent = null;
    meteo.donnees!.conditions.pluieDerniereHeureMillimetres = null;
    const fixture = await ouvrir();
    expect(texte(fixture.nativeElement)).toContain('Non disponible');
    expect(texte(fixture.nativeElement)).not.toContain('0 °C');
  });

  it('signale les données périmées tout en conservant leurs dates', async () => {
    meteo.statut = 'PERIMEE';
    meteo.message = 'Mise à jour indisponible';
    const fixture = await ouvrir();
    expect(texte(fixture.nativeElement)).toContain('Dernières données connues');
    expect(texte(fixture.nativeElement)).toContain('13:10');
    expect(texte(fixture.nativeElement)).not.toContain('Données disponibles');
  });

  it('masque les mesures lorsque le service se déclare indisponible', async () => {
    meteo.statut = 'INDISPONIBLE';
    meteo.message = 'Le quota est temporairement atteint';
    const fixture = await ouvrir();
    expect(texte(fixture.nativeElement)).toContain('quota');
    expect(texte(fixture.nativeElement)).not.toContain('0 °C');
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(0);
  });

  for (const r of ['ADMIN', 'AGRICULTEUR', 'AGRONOME']) {
    it('présente la localisation manquante selon le rôle ' + r, async () => {
      role = r;
      meteo.statut = 'LOCALISATION_MANQUANTE';
      meteo.donnees = null;
      const fixture = await ouvrir();
      const lien = fixture.nativeElement.querySelector('a[href="/exploitations/7/modifier"]');
      expect(lien !== null).toBe(r === 'ADMIN');
      expect(texte(fixture.nativeElement)).toContain('Localisation météo non renseignée');
    });
  }

  it('explique une localisation invalide', async () => {
    meteo.statut = 'LOCALISATION_INVALIDE';
    meteo.donnees = null;
    const fixture = await ouvrir();
    expect(texte(fixture.nativeElement)).toContain('Localisation météo à corriger');
  });

  it('pagine les prévisions sans rappeler le fournisseur', async () => {
    const fixture = await ouvrir();
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(10);
    fixture.componentInstance.changerPage(1);
    await fixture.whenStable();
    expect(fixture.componentInstance.previsionsPage()[0].datePrevision).toBe(
      meteo.donnees!.previsions[10].datePrevision,
    );
    fixture.componentInstance.changerTaille(20);
    await fixture.whenStable();
    expect(fixture.componentInstance.page()).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(20);
    expect(service.consulterParParcelle).toHaveBeenCalledTimes(1);
  });

  it('présente une liste de prévisions vide sans fausse pagination', async () => {
    meteo.donnees!.previsions = [];
    const fixture = await ouvrir();
    expect(texte(fixture.nativeElement)).toContain('Aucune prévision disponible');
    expect(fixture.nativeElement.querySelector('app-pagination')).toBeNull();
  });

  it('efface les anciennes données et les prévisions après un refus d’accès', async () => {
    const fixture = await ouvrir();
    service.consulterParParcelle.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(fixture.componentInstance.meteo()).toBeNull();
    expect(fixture.componentInstance.previsions()).toEqual([]);
    expect(texte(fixture.nativeElement)).not.toContain('0 °C');
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('droits');
  });

  it('présente une erreur réseau et une session expirée', async () => {
    service.consulterParParcelle.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );
    const fixture = await ouvrir();
    expect(texte(fixture.nativeElement)).toContain('Impossible de charger la météo');
    service.consulterParParcelle.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(texte(fixture.nativeElement)).toContain('session a expiré');
  });

  it('annule l’ancienne requête au changement de parcelle et à la destruction', async () => {
    const ancienne = new Subject<MeteoResponse>();
    service.consulterParParcelle.mockReturnValueOnce(ancienne);
    const fixture = await ouvrir();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain(
      'Chargement',
    );
    fixture.componentRef.setInput('parcelleId', 12);
    await fixture.whenStable();
    expect(ancienne.observed).toBe(false);
    expect(service.consulterParParcelle).toHaveBeenLastCalledWith(12);
    service.consulterParParcelle.mockReturnValue(ancienne);
    fixture.componentInstance.charger();
    fixture.destroy();
    expect(ancienne.observed).toBe(false);
  });

  it('ne lance pas de requête pour un identifiant invalide', async () => {
    const fixture = TestBed.createComponent(MeteoParcelle);
    fixture.componentRef.setInput('parcelleId', -1);
    await fixture.whenStable();
    expect(service.consulterParParcelle).not.toHaveBeenCalled();
  });

  it('conserve le détail et les plantations quand la météo est en erreur', async () => {
    role = 'AGRICULTEUR';
    TestBed.overrideProvider(ParcelleService, {
      useValue: {
        consulterParId: () =>
          of({
            id: 11,
            code: 'PAR-11',
            nom: 'Oliviers',
            superficie: 5,
            exploitationId: 7,
            exploitationNom: 'Ferme Sfax',
            exploitationCode: 'EXP-07',
          }),
      },
    });
    TestBed.overrideProvider(PlantationService, {
      useValue: {
        consulterPage: () =>
          of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
      },
    });
    TestBed.overrideProvider(CampagneService, { useValue: { consulterToutes: () => of([]) } });
    service.consulterParParcelle.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/parcelles/11');
    expect(texte(navigation.routeNativeElement!)).toContain('Oliviers');
    expect(texte(navigation.routeNativeElement!)).toContain('Plantations de la parcelle');
    expect(texte(navigation.routeNativeElement!)).toContain('Impossible de charger la météo');
  });
});

describe('Service HTTP météo', () => {
  it('appelle uniquement SmartAgri sans clé fournisseur', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(MeteoService);
    const http = TestBed.inject(HttpTestingController);
    service.consulterParParcelle(11).subscribe();
    const requete = http.expectOne('http://localhost:8081/api/parcelles/11/meteo');
    expect(requete.request.method).toBe('GET');
    expect(requete.request.params.keys()).toEqual([]);
    requete.flush({ statut: 'INDISPONIBLE', donnees: null });
    http.verify();
  });
});
