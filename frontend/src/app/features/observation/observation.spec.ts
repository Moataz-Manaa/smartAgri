import { TraitementService } from '../../core/services/traitement';
import { FertilisationService } from '../../core/services/fertilisation';
import { IrrigationService } from '../../core/services/irrigation';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/services/auth';
import { ObservationService } from '../../core/services/observation';
import { PlantationService } from '../../core/services/plantation';
import { ObservationResponse } from '../../core/models/observation';
import { PlantationResponse } from '../../core/models/plantation';
import { ObservationFormulaire } from './observation-formulaire/observation-formulaire';
import { ObservationListe } from './observation-liste/observation-liste';

describe('Observations agricoles', () => {
  let role: string;
  let plantation: PlantationResponse;
  let observation: ObservationResponse;
  let service: {
    consulterParId: ReturnType<typeof vi.fn>;
    consulterPage: ReturnType<typeof vi.fn>;
    creer: ReturnType<typeof vi.fn>;
    modifier: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    role = 'AGRICULTEUR';
    plantation = {
      id: 9,
      parcelleId: 11,
      parcelleCode: 'PAR-11',
      parcelleNom: 'Oliviers',
      campagneId: 7,
      campagneNom: 'Suivi 2020',
      campagneCloturee: false,
      varieteId: 3,
      varieteNom: 'Chemlali',
      cultureId: 2,
      cultureNom: 'Olivier',
      datePlantation: '2010-01-01',
      superficie: 2,
      quantitePlantee: null,
      dateRecoltePrevue: null,
      commentaire: null,
      statut: 'EN_COURS',
    };
    observation = {
      id: 5,
      plantationId: 9,
      dateObservation: '2020-06-01',
      description: 'Feuilles jaunes',
      auteurNom: 'Ali',
      auteurPrenom: 'Ahmed',
      campagneCloturee: false,
    };
    service = {
      consulterParId: vi.fn(() => of(observation)),
      consulterPage: vi.fn((plantationId: number, page: number, taille: number) =>
        of({ contenu: [observation], page, taille, totalElements: 1, totalPages: 1 }),
      ),
      creer: vi.fn(() => of(observation)),
      modifier: vi.fn(() => of(observation)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: TraitementService,
          useValue: {
            consulterPage: () =>
              of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
          },
        },
        {
          provide: FertilisationService,
          useValue: {
            consulterPage: () =>
              of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
          },
        },
        {
          provide: IrrigationService,
          useValue: {
            consulterPage: () =>
              of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
            consulterTotal: () => of({ volumeTotalMetresCubes: 0 }),
          },
        },
        {
          provide: AuthService,
          useValue: { estConnecte: () => true, possedeRole: (valeur: string) => role === valeur },
        },
        { provide: PlantationService, useValue: { consulterParId: () => of(plantation) } },
        { provide: ObservationService, useValue: service },
      ],
    });
  });

  function saisir(page: HTMLElement, id: string, valeur: string): void {
    const input = page.querySelector<HTMLInputElement>('#' + id)!;
    input.value = valeur;
    input.dispatchEvent(new Event('input'));
  }

  function soumettre(page: HTMLElement): void {
    page
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  }

  it('enregistre depuis la plantation sans envoyer un auteur choisi dans le formulaire', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/observations/nouvelle?plantationId=9', ObservationFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'date-observation', '2020-06-01');
    saisir(page, 'description-observation', '  Feuilles jaunes  ');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.creer).toHaveBeenCalledWith({
      plantationId: 9,
      dateObservation: '2020-06-01',
      description: 'Feuilles jaunes',
    });
    expect(TestBed.inject(Router).url).toBe('/plantations/9');
    expect(navigation.routeNativeElement!.textContent).toContain('Ahmed Ali');
  });

  it('modifie la description en conservant le contexte et l’auteur en lecture seule', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/observations/5/modifier', ObservationFormulaire);
    const page = navigation.routeNativeElement!;
    expect(page.textContent).toContain('Ahmed Ali');
    expect(page.querySelector('[name="auteur"]')).toBeNull();
    saisir(page, 'description-observation', 'Observation corrigee');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.modifier).toHaveBeenCalledWith(5, {
      plantationId: 9,
      dateObservation: '2020-06-01',
      description: 'Observation corrigee',
    });
  });

  it('refuse le texte vide et une date future', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/observations/5/modifier', ObservationFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'description-observation', '  ');
    await navigation.fixture.whenStable();
    soumettre(page);
    saisir(page, 'description-observation', 'Observation');
    saisir(page, 'date-observation', '2999-01-01');
    await navigation.fixture.whenStable();
    soumettre(page);
    expect(service.modifier).not.toHaveBeenCalled();
  });

  it('bloque la modification après clôture même par URL directe', async () => {
    plantation.campagneCloturee = true;
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/observations/5/modifier', ObservationFormulaire);
    const page = navigation.routeNativeElement!;
    expect(page.querySelector<HTMLFieldSetElement>('fieldset')!.disabled).toBe(true);
    expect(page.querySelector('button[type="submit"]')).toBeNull();
    soumettre(page);
    expect(service.modifier).not.toHaveBeenCalled();
  });

  for (const metier of ['ADMIN', 'AGRONOME']) {
    it('permet la consultation et refuse les formulaires pour ' + metier, async () => {
      role = metier;
      const navigation = await RouterTestingHarness.create();
      await navigation.navigateByUrl('/plantations/9');
      expect(navigation.routeNativeElement!.textContent).toContain('Feuilles jaunes');
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/observations/5/modifier"]'),
      ).toBeNull();
      await navigation.navigateByUrl('/observations/nouvelle?plantationId=9');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
      await navigation.navigateByUrl('/observations/5/modifier');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
    });
  }

  it('efface les observations et les totaux après un refus d’accès', async () => {
    const fixture = TestBed.createComponent(ObservationListe);
    fixture.componentRef.setInput('plantationId', 9);
    await fixture.whenStable();
    service.consulterPage.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(fixture.componentInstance.observations()).toEqual([]);
    expect(fixture.componentInstance.totalElements()).toBe(0);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('droits');
  });

  it('transmet la pagination et annule la requête précédente', async () => {
    const ancienne = new Subject<never>();
    service.consulterPage.mockReturnValueOnce(ancienne);
    const fixture = TestBed.createComponent(ObservationListe);
    fixture.componentRef.setInput('plantationId', 9);
    await fixture.whenStable();
    fixture.componentInstance.changerTaille(20);
    await fixture.whenStable();
    expect(ancienne.observed).toBe(false);
    expect(service.consulterPage).toHaveBeenLastCalledWith(9, 0, 20);
    expect(fixture.componentInstance.observations()[0].id).toBe(5);
  });

  it('bloque une double soumission pendant l’enregistrement', async () => {
    service.modifier.mockReturnValue(new Subject<ObservationResponse>());
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/observations/5/modifier', ObservationFormulaire);
    soumettre(navigation.routeNativeElement!);
    soumettre(navigation.routeNativeElement!);
    expect(service.modifier).toHaveBeenCalledTimes(1);
  });
});
