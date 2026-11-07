import { TraitementService } from '../../core/services/traitement';
import { FertilisationService } from '../../core/services/fertilisation';
import { ObservationService } from '../../core/services/observation';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/services/auth';
import { IrrigationService } from '../../core/services/irrigation';
import { PlantationService } from '../../core/services/plantation';
import { IrrigationResponse } from '../../core/models/irrigation';
import { PlantationResponse } from '../../core/models/plantation';
import { IrrigationFormulaire } from './irrigation-formulaire/irrigation-formulaire';
import { IrrigationListe } from './irrigation-liste/irrigation-liste';

describe('Irrigations agricoles', () => {
  let role: string;
  let plantation: PlantationResponse;
  let irrigation: IrrigationResponse;
  let service: {
    consulterParId: ReturnType<typeof vi.fn>;
    consulterPage: ReturnType<typeof vi.fn>;
    creer: ReturnType<typeof vi.fn>;
    modifier: ReturnType<typeof vi.fn>;
    consulterTotal: ReturnType<typeof vi.fn>;
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
    irrigation = {
      id: 5,
      plantationId: 9,
      dateIrrigation: '2020-06-01',
      commentaire: 'Irrigation matinale',
      volume: 500,
      unite: 'LITRE',
      volumeMetresCubes: 0.5,
      auteurNom: 'Ali',
      auteurPrenom: 'Ahmed',
      campagneCloturee: false,
    };
    service = {
      consulterParId: vi.fn(() => of(irrigation)),
      consulterPage: vi.fn((plantationId: number, page: number, taille: number) =>
        of({ contenu: [irrigation], page, taille, totalElements: 1, totalPages: 1 }),
      ),
      creer: vi.fn(() => of(irrigation)),
      modifier: vi.fn(() => of(irrigation)),
      consulterTotal: vi.fn(() => of({ volumeTotalMetresCubes: 2.5 })),
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
          provide: ObservationService,
          useValue: {
            consulterPage: () =>
              of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
          },
        },
        {
          provide: AuthService,
          useValue: { estConnecte: () => true, possedeRole: (valeur: string) => role === valeur },
        },
        { provide: PlantationService, useValue: { consulterParId: () => of(plantation) } },
        { provide: IrrigationService, useValue: service },
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
    await navigation.navigateByUrl('/irrigations/nouvelle?plantationId=9', IrrigationFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'date-irrigation', '2020-06-01');
    saisir(page, 'commentaire-irrigation', '  Irrigation matinale  ');
    saisir(page, 'volume-irrigation', '500');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.creer).toHaveBeenCalledWith({
      plantationId: 9,
      dateIrrigation: '2020-06-01',
      commentaire: 'Irrigation matinale',
      volume: 500,
      unite: 'LITRE',
    });
    expect(TestBed.inject(Router).url).toBe('/plantations/9');
    expect(navigation.routeNativeElement!.textContent).toContain('Ahmed Ali');
  });

  it('modifie la description en conservant le contexte et l’auteur en lecture seule', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/irrigations/5/modifier', IrrigationFormulaire);
    const page = navigation.routeNativeElement!;
    expect(page.textContent).toContain('Ahmed Ali');
    expect(page.querySelector('[name="auteur"]')).toBeNull();
    saisir(page, 'commentaire-irrigation', 'Irrigation corrigee');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.modifier).toHaveBeenCalledWith(5, {
      plantationId: 9,
      dateIrrigation: '2020-06-01',
      commentaire: 'Irrigation corrigee',
      volume: 500,
      unite: 'LITRE',
    });
  });

  it('refuse un volume nul et une date future', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/irrigations/5/modifier', IrrigationFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'volume-irrigation', '0');
    await navigation.fixture.whenStable();
    soumettre(page);
    saisir(page, 'volume-irrigation', '500');
    saisir(page, 'date-irrigation', '2999-01-01');
    await navigation.fixture.whenStable();
    soumettre(page);
    expect(service.modifier).not.toHaveBeenCalled();
  });

  it('bloque la modification après clôture même par URL directe', async () => {
    plantation.campagneCloturee = true;
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/irrigations/5/modifier', IrrigationFormulaire);
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
      expect(navigation.routeNativeElement!.textContent).toContain('Irrigation matinale');
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/irrigations/5/modifier"]'),
      ).toBeNull();
      await navigation.navigateByUrl('/irrigations/nouvelle?plantationId=9');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
      await navigation.navigateByUrl('/irrigations/5/modifier');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
    });
  }

  it('efface les irrigations et les totaux après un refus d’accès', async () => {
    const fixture = TestBed.createComponent(IrrigationListe);
    fixture.componentRef.setInput('plantationId', 9);
    await fixture.whenStable();
    service.consulterPage.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(fixture.componentInstance.irrigations()).toEqual([]);
    expect(fixture.componentInstance.totalElements()).toBe(0);
    expect(fixture.componentInstance.volumeTotal()).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('droits');
  });

  it('transmet la pagination et annule la requête précédente', async () => {
    const ancienne = new Subject<never>();
    service.consulterPage.mockReturnValueOnce(ancienne);
    const fixture = TestBed.createComponent(IrrigationListe);
    fixture.componentRef.setInput('plantationId', 9);
    await fixture.whenStable();
    fixture.componentInstance.changerTaille(20);
    await fixture.whenStable();
    expect(ancienne.observed).toBe(false);
    expect(service.consulterPage).toHaveBeenLastCalledWith(9, 0, 20);
    expect(fixture.componentInstance.irrigations()[0].id).toBe(5);
    expect(fixture.componentInstance.volumeTotal()).toBe(2.5);
  });

  it('bloque une double soumission pendant l’enregistrement', async () => {
    service.modifier.mockReturnValue(new Subject<IrrigationResponse>());
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/irrigations/5/modifier', IrrigationFormulaire);
    soumettre(navigation.routeNativeElement!);
    soumettre(navigation.routeNativeElement!);
    expect(service.modifier).toHaveBeenCalledTimes(1);
  });
  it('enregistre des metres cubes et affiche le total fourni pour toutes les pages', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/irrigations/5/modifier', IrrigationFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'volume-irrigation', '2');
    const unite = page.querySelector<HTMLSelectElement>('#unite-irrigation')!;
    unite.value = 'METRE_CUBE';
    unite.dispatchEvent(new Event('change'));
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.modifier).toHaveBeenCalledWith(5, {
      plantationId: 9,
      dateIrrigation: '2020-06-01',
      volume: 2,
      unite: 'METRE_CUBE',
      commentaire: 'Irrigation matinale',
    });
    expect(
      navigation.routeNativeElement!.querySelector('[data-testid="total-irrigation"]')!.textContent,
    ).toContain('2.5');
    expect(service.consulterTotal).toHaveBeenCalledWith(9);
  });
});
