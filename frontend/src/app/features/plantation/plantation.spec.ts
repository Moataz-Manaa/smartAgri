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
import { PlantationService } from '../../core/services/plantation';
import { ObservationService } from '../../core/services/observation';
import { ParcelleService } from '../../core/services/parcelle';
import { CampagneService } from '../../core/services/campagne';
import { CultureService } from '../../core/services/culture';
import { VarieteService } from '../../core/services/variete';
import { PlantationResponse } from '../../core/models/plantation';
import { PlantationListe } from './plantation-liste/plantation-liste';
import { PlantationFormulaire } from './plantation-formulaire/plantation-formulaire';
import { PlantationDetail } from './plantation-detail/plantation-detail';

describe('Plantations par campagne', () => {
  let role: string;
  let plantation: PlantationResponse;
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
      campagneNom: 'Suivi 2027',
      campagneCloturee: false,
      varieteId: 3,
      varieteNom: 'Chemlali',
      cultureId: 2,
      cultureNom: 'Olivier',
      datePlantation: '2010-01-01',
      superficie: 2,
      quantitePlantee: null,
      dateRecoltePrevue: '2027-10-15',
      commentaire: null,
      statut: 'EN_COURS',
    };
    service = {
      consulterParId: vi.fn(() => of(plantation)),
      consulterPage: vi.fn(() =>
        of({ contenu: [plantation], page: 0, taille: 10, totalElements: 1, totalPages: 1 }),
      ),
      creer: vi.fn(() => of(plantation)),
      modifier: vi.fn(() => of(plantation)),
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
        { provide: PlantationService, useValue: service },
        {
          provide: ObservationService,
          useValue: {
            consulterPage: () =>
              of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
          },
        },
        {
          provide: ParcelleService,
          useValue: {
            consulterToutes: () =>
              of([
                { id: 11, code: 'PAR-11', nom: 'Oliviers', superficie: 5 },
                { id: 12, code: 'PAR-12', nom: 'Tomates', superficie: 3 },
              ]),
          },
        },
        {
          provide: CampagneService,
          useValue: {
            consulterToutes: () =>
              of([
                {
                  id: 7,
                  nom: 'Suivi 2027',
                  statut: 'EN_COURS',
                  parcelleIds: [11],
                  dateDebut: '2027-01-01',
                  dateFin: '2027-12-31',
                },
                {
                  id: 8,
                  nom: 'Autre parcelle',
                  statut: 'PLANIFIEE',
                  parcelleIds: [12],
                  dateDebut: '2027-01-01',
                  dateFin: '2027-12-31',
                },
                {
                  id: 10,
                  nom: 'Ancienne',
                  statut: 'CLOTUREE',
                  parcelleIds: [11],
                  dateDebut: '2026-01-01',
                  dateFin: '2026-12-31',
                },
              ]),
          },
        },
        {
          provide: CultureService,
          useValue: {
            consulterToutes: () =>
              of([
                { id: 2, nom: 'Olivier' },
                { id: 4, nom: 'Tomate' },
              ]),
          },
        },
        {
          provide: VarieteService,
          useValue: {
            consulterToutes: () =>
              of([
                { id: 3, nom: 'Chemlali', cultureId: 2 },
                { id: 5, nom: 'Roma', cultureId: 4 },
              ]),
          },
        },
      ],
    });
  });

  function choisir(page: HTMLElement, id: string, texte: string): void {
    const select = page.querySelector<HTMLSelectElement>('#' + id)!;
    const option = Array.from(select.options).find((element) =>
      element.textContent?.includes(texte),
    )!;
    select.value = option.value;
    select.dispatchEvent(new Event('change'));
  }

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

  it('filtre les campagnes de la parcelle et réinitialise les choix dépendants', async () => {
    const fixture = TestBed.createComponent(PlantationFormulaire);
    await fixture.whenStable();
    const page: HTMLElement = fixture.nativeElement;
    choisir(page, 'parcelle', 'PAR-11');
    await fixture.whenStable();
    expect(page.querySelector('#campagne')!.textContent).toContain('Suivi 2027');
    expect(page.querySelector('#campagne')!.textContent).not.toContain('Autre parcelle');
    expect(page.querySelector('#campagne')!.textContent).not.toContain('Ancienne');
    choisir(page, 'campagne', 'Suivi 2027');
    choisir(page, 'culture', 'Olivier');
    await fixture.whenStable();
    choisir(page, 'variete', 'Chemlali');
    choisir(page, 'culture', 'Tomate');
    choisir(page, 'parcelle', 'PAR-12');
    await fixture.whenStable();
    expect(fixture.componentInstance.varieteId).toBeNull();
    expect(fixture.componentInstance.campagneId).toBeNull();
    expect(page.querySelector('#variete')!.textContent).toContain('Roma');
    expect(page.querySelector('#variete')!.textContent).not.toContain('Chemlali');
  });

  it('crée une fiche avec une date ancienne et une variété cohérente', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/plantations/nouvelle?parcelleId=11', PlantationFormulaire);
    const page = navigation.routeNativeElement!;
    choisir(page, 'campagne', 'Suivi 2027');
    choisir(page, 'culture', 'Olivier');
    await navigation.fixture.whenStable();
    choisir(page, 'variete', 'Chemlali');
    saisir(page, 'date-plantation', '2010-01-01');
    saisir(page, 'superficie', '2');
    saisir(page, 'recolte-prevue', '2027-10-15');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.creer).toHaveBeenCalledWith({
      parcelleId: 11,
      campagneId: 7,
      varieteId: 3,
      datePlantation: '2010-01-01',
      superficie: 2,
      quantitePlantee: null,
      dateRecoltePrevue: '2027-10-15',
      commentaire: null,
      statut: 'EN_COURS',
    });
    expect(TestBed.inject(Router).url).toBe('/plantations/9');
  });

  it('refuse une superficie supérieure à la parcelle et une récolte hors campagne', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/plantations/9/modifier', PlantationFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'superficie', '6');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.modifier).not.toHaveBeenCalled();
    saisir(page, 'superficie', '2');
    saisir(page, 'recolte-prevue', '2028-10-15');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.modifier).not.toHaveBeenCalled();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('dates');
  });

  it('conserve la campagne clôturée en lecture seule par URL directe', async () => {
    plantation.campagneCloturee = true;
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/plantations/9/modifier', PlantationFormulaire);
    const page = navigation.routeNativeElement!;
    expect(page.querySelector<HTMLFieldSetElement>('fieldset')!.disabled).toBe(true);
    expect(page.querySelector('button[type="submit"]')).toBeNull();
    soumettre(page);
    expect(service.modifier).not.toHaveBeenCalled();
  });

  for (const metier of ['ADMIN', 'AGRONOME']) {
    it('autorise la consultation et refuse la saisie pour ' + metier, async () => {
      role = metier;
      const navigation = await RouterTestingHarness.create();
      await navigation.navigateByUrl('/plantations', PlantationListe);
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/plantations/nouvelle"]'),
      ).toBeNull();
      await navigation.navigateByUrl('/plantations/9', PlantationDetail);
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/plantations/9/modifier"]'),
      ).toBeNull();
      await navigation.navigateByUrl('/plantations/nouvelle');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
      await navigation.navigateByUrl('/plantations/9/modifier');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
    });
  }

  it('annule une ancienne réponse lorsque le filtre change', async () => {
    const ancienne = new Subject<never>();
    service.consulterPage.mockReturnValueOnce(ancienne);
    const fixture = TestBed.createComponent(PlantationListe);
    await fixture.whenStable();
    fixture.componentInstance.campagneId = 7;
    fixture.componentInstance.statut = 'EN_COURS';
    fixture.componentInstance.charger(0);
    expect(service.consulterPage).toHaveBeenLastCalledWith(0, 10, null, 7, 'EN_COURS');
    expect(ancienne.observed).toBe(false);
    expect(fixture.componentInstance.plantations()[0].id).toBe(9);
  });

  it('charge les plantations dans le détail d’une parcelle avec son identifiant', async () => {
    TestBed.overrideProvider(ParcelleService, {
      useValue: {
        consulterParId: () =>
          of({ id: 11, code: 'PAR-11', nom: 'Oliviers', superficie: 5, exploitationId: 1 }),
      },
    });
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/parcelles/11');
    expect(service.consulterPage).toHaveBeenCalledWith(0, 10, 11, null, null);
    expect(navigation.routeNativeElement!.textContent).toContain('Plantations de la parcelle');
  });

  it('efface la liste et ses totaux en cas de retrait d’accès', async () => {
    const fixture = TestBed.createComponent(PlantationListe);
    await fixture.whenStable();
    service.consulterPage.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(fixture.componentInstance.plantations()).toEqual([]);
    expect(fixture.componentInstance.totalElements()).toBe(0);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('droits');
  });

  it('évite une double soumission pendant la réponse du backend', async () => {
    service.modifier.mockReturnValue(new Subject<PlantationResponse>());
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/plantations/9/modifier', PlantationFormulaire);
    soumettre(navigation.routeNativeElement!);
    soumettre(navigation.routeNativeElement!);
    expect(service.modifier).toHaveBeenCalledTimes(1);
  });
});
