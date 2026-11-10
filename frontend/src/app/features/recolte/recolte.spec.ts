import { HistoriqueService } from '../../core/services/historique';
import { IrrigationService } from '../../core/services/irrigation';
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
import { RecolteService } from '../../core/services/recolte';
import { PlantationService } from '../../core/services/plantation';
import { RecolteResponse } from '../../core/models/recolte';
import { PlantationResponse } from '../../core/models/plantation';
import { RecolteFormulaire } from './recolte-formulaire/recolte-formulaire';
import { RecolteListe } from './recolte-liste/recolte-liste';

describe('Recoltes agricoles', () => {
  let role: string;
  let plantation: PlantationResponse;
  let recolte: RecolteResponse;
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
    recolte = {
      id: 5,
      plantationId: 9,
      dateRecolte: '2020-06-01',
      commentaire: 'Recolte matinale',
      quantite: 500,
      unite: 'KILOGRAMME',
      quantiteKilogrammes: 500,
      auteurNom: 'Ali',
      auteurPrenom: 'Ahmed',
      campagneCloturee: false,
    };
    service = {
      consulterParId: vi.fn(() => of(recolte)),
      consulterPage: vi.fn((plantationId: number, page: number, taille: number) =>
        of({ contenu: [recolte], page, taille, totalElements: 1, totalPages: 1 }),
      ),
      creer: vi.fn(() => of(recolte)),
      modifier: vi.fn(() => of(recolte)),
      consulterTotal: vi.fn(() =>
        of({ quantiteTotaleKilogrammes: 2500, superficieHectares: 2, rendementKgParHectare: 1250 }),
      ),
    };
    TestBed.configureTestingModule({
      providers: [
        {
          provide: HistoriqueService,
          useValue: {
            consulterPage: () =>
              of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
          },
        },
        provideRouter(routes),
        {
          provide: IrrigationService,
          useValue: {
            consulterPage: () =>
              of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
            consulterTotal: () => of({ volumeTotalMetresCubes: 0 }),
          },
        },

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
        { provide: RecolteService, useValue: service },
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
    await navigation.navigateByUrl('/recoltes/nouvelle?plantationId=9', RecolteFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'date-recolte', '2020-06-01');
    saisir(page, 'commentaire-recolte', '  Recolte matinale  ');
    saisir(page, 'quantite-recolte', '500');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.creer).toHaveBeenCalledWith({
      plantationId: 9,
      dateRecolte: '2020-06-01',
      commentaire: 'Recolte matinale',
      quantite: 500,
      unite: 'KILOGRAMME',
    });
    expect(TestBed.inject(Router).url).toBe('/plantations/9');
    expect(navigation.routeNativeElement!.textContent).toContain('Ahmed Ali');
  });

  it('modifie la description en conservant le contexte et l’auteur en lecture seule', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/recoltes/5/modifier', RecolteFormulaire);
    const page = navigation.routeNativeElement!;
    expect(page.textContent).toContain('Ahmed Ali');
    expect(page.querySelector('[name="auteur"]')).toBeNull();
    saisir(page, 'commentaire-recolte', 'Recolte corrigee');
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.modifier).toHaveBeenCalledWith(5, {
      plantationId: 9,
      dateRecolte: '2020-06-01',
      commentaire: 'Recolte corrigee',
      quantite: 500,
      unite: 'KILOGRAMME',
    });
  });

  it('refuse une quantite nul et une date future', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/recoltes/5/modifier', RecolteFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'quantite-recolte', '0');
    await navigation.fixture.whenStable();
    soumettre(page);
    saisir(page, 'quantite-recolte', '500');
    saisir(page, 'date-recolte', '2999-01-01');
    await navigation.fixture.whenStable();
    soumettre(page);
    expect(service.modifier).not.toHaveBeenCalled();
  });

  it('bloque la modification après clôture même par URL directe', async () => {
    plantation.campagneCloturee = true;
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/recoltes/5/modifier', RecolteFormulaire);
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
      expect(navigation.routeNativeElement!.textContent).toContain('Recolte matinale');
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/recoltes/5/modifier"]'),
      ).toBeNull();
      await navigation.navigateByUrl('/recoltes/nouvelle?plantationId=9');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
      await navigation.navigateByUrl('/recoltes/5/modifier');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
    });
  }

  it('efface les recoltes et les totaux après un refus d’accès', async () => {
    const fixture = TestBed.createComponent(RecolteListe);
    fixture.componentRef.setInput('plantationId', 9);
    await fixture.whenStable();
    service.consulterPage.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(fixture.componentInstance.recoltes()).toEqual([]);
    expect(fixture.componentInstance.totalElements()).toBe(0);
    expect(fixture.componentInstance.quantiteTotale()).toBeNull();
    expect(fixture.componentInstance.rendement()).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('droits');
  });

  it('transmet la pagination et annule la requête précédente', async () => {
    const ancienne = new Subject<never>();
    service.consulterPage.mockReturnValueOnce(ancienne);
    const fixture = TestBed.createComponent(RecolteListe);
    fixture.componentRef.setInput('plantationId', 9);
    await fixture.whenStable();
    fixture.componentInstance.changerTaille(20);
    await fixture.whenStable();
    expect(ancienne.observed).toBe(false);
    expect(service.consulterPage).toHaveBeenLastCalledWith(9, 0, 20);
    expect(fixture.componentInstance.recoltes()[0].id).toBe(5);
    expect(fixture.componentInstance.quantiteTotale()).toBe(2500);
  });

  it('bloque une double soumission pendant l’enregistrement', async () => {
    service.modifier.mockReturnValue(new Subject<RecolteResponse>());
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/recoltes/5/modifier', RecolteFormulaire);
    soumettre(navigation.routeNativeElement!);
    soumettre(navigation.routeNativeElement!);
    expect(service.modifier).toHaveBeenCalledTimes(1);
  });
  it('enregistre des tonnes et affiche le total fourni pour toutes les pages', async () => {
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/recoltes/5/modifier', RecolteFormulaire);
    const page = navigation.routeNativeElement!;
    saisir(page, 'quantite-recolte', '2');
    const unite = page.querySelector<HTMLSelectElement>('#unite-recolte')!;
    unite.value = 'TONNE';
    unite.dispatchEvent(new Event('change'));
    await navigation.fixture.whenStable();
    soumettre(page);
    await navigation.fixture.whenStable();
    expect(service.modifier).toHaveBeenCalledWith(5, {
      plantationId: 9,
      dateRecolte: '2020-06-01',
      quantite: 2,
      unite: 'TONNE',
      commentaire: 'Recolte matinale',
    });
    expect(
      navigation.routeNativeElement!.querySelector('[data-testid="total-recolte"]')!.textContent,
    ).toContain('2,500');
    expect(service.consulterTotal).toHaveBeenCalledWith(9);
  });
  it('affiche un total nul et aucun rendement avant la premiere recolte', async () => {
    service.consulterPage.mockReturnValue(
      of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
    );
    service.consulterTotal.mockReturnValue(
      of({ quantiteTotaleKilogrammes: 0, superficieHectares: 2, rendementKgParHectare: null }),
    );
    const fixture = TestBed.createComponent(RecolteListe);
    fixture.componentRef.setInput('plantationId', 9);
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('[data-testid="total-recolte"]').textContent,
    ).toContain('0 kg');
    expect(
      fixture.nativeElement.querySelector('[data-testid="rendement-recolte"]').textContent,
    ).toContain('Non disponible');
  });
});
