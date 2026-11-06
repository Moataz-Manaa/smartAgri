import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CultureFormulaire } from './culture-formulaire/culture-formulaire';
import { VarieteFormulaire } from '../variete/variete-formulaire/variete-formulaire';
import { VarieteListe } from '../variete/variete-liste/variete-liste';
import { CultureListe } from './culture-liste/culture-liste';
import { AuthService } from '../../core/services/auth';
import { CultureService } from '../../core/services/culture';
import { VarieteService } from '../../core/services/variete';
import { CultureResponse } from '../../core/models/culture';
import { VarieteResponse } from '../../core/models/variete';
import { PageResponse } from '../../core/models/page';
import { routes } from '../../app.routes';

describe('Catalogue agricole', () => {
  const olivier: CultureResponse = {
    id: 7,
    nom: 'Olivier',
    type: 'Arboriculture',
    description: null,
  };
  const tomate: CultureResponse = { id: 8, nom: 'Tomate', type: null, description: null };
  const chemlali: VarieteResponse = {
    id: 3,
    nom: 'Chemlali',
    description: null,
    cultureId: 7,
    cultureNom: 'Olivier',
  };
  let role: string;
  let cultureService: {
    consulterToutes: ReturnType<typeof vi.fn>;
    consulterPage: ReturnType<typeof vi.fn>;
    consulterParId: ReturnType<typeof vi.fn>;
    creer: ReturnType<typeof vi.fn>;
    modifier: ReturnType<typeof vi.fn>;
  };
  let varieteService: typeof cultureService;
  beforeEach(() => {
    role = 'ADMIN';
    cultureService = {
      consulterToutes: vi.fn(() => of([olivier, tomate])),
      consulterPage: vi.fn(() =>
        of({ contenu: [olivier, tomate], page: 0, taille: 10, totalElements: 2, totalPages: 1 }),
      ),
      consulterParId: vi.fn(() => of(olivier)),
      creer: vi.fn(() => of(olivier)),
      modifier: vi.fn(() => of(olivier)),
    };
    varieteService = {
      consulterToutes: vi.fn(() => of([chemlali])),
      consulterPage: vi.fn((page: number, taille: number, cultureId: number | null) =>
        of({
          contenu: cultureId === 8 ? [] : [chemlali],
          page,
          taille,
          totalElements: cultureId === 8 ? 0 : 1,
          totalPages: cultureId === 8 ? 0 : 1,
        }),
      ),
      consulterParId: vi.fn(() => of(chemlali)),
      creer: vi.fn(() => of(chemlali)),
      modifier: vi.fn(() => of(chemlali)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: { estConnecte: () => true, possedeRole: (valeur: string) => valeur === role },
        },
        { provide: CultureService, useValue: cultureService },
        { provide: VarieteService, useValue: varieteService },
      ],
    });
  });

  it('enregistre une culture avec les valeurs nettoyées du formulaire', async () => {
    const fixture = TestBed.createComponent(CultureFormulaire);
    await fixture.whenStable();
    const nom: HTMLInputElement = fixture.nativeElement.querySelector('#nom');
    nom.value = ' Olivier ';
    nom.dispatchEvent(new Event('input'));
    const type: HTMLInputElement = fixture.nativeElement.querySelector('#type');
    type.value = ' Arboriculture ';
    type.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(cultureService.creer).toHaveBeenCalledWith({
      nom: 'Olivier',
      type: 'Arboriculture',
      description: null,
    });
    expect(TestBed.inject(Router).url).toBe('/cultures');
  });

  it('refuse un formulaire de culture vide', async () => {
    const fixture = TestBed.createComponent(CultureFormulaire);
    await fixture.whenStable();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(cultureService.creer).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#nom-erreur').textContent).toContain('obligatoire');
  });

  it('charge puis modifie la culture sans lancer une création', async () => {
    const navigation = await RouterTestingHarness.create();
    const formulaire = await navigation.navigateByUrl('/cultures/7/modifier', CultureFormulaire);
    expect(formulaire.nom).toBe('Olivier');
    const nom = navigation.routeNativeElement!.querySelector<HTMLInputElement>('#nom')!;
    nom.value = 'Olivier modifié';
    nom.dispatchEvent(new Event('input'));
    await navigation.fixture.whenStable();
    navigation
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await navigation.fixture.whenStable();
    expect(cultureService.modifier).toHaveBeenCalledWith(7, {
      nom: 'Olivier modifié',
      type: 'Arboriculture',
      description: null,
    });
    expect(cultureService.creer).not.toHaveBeenCalled();
  });

  it('affiche un doublon sans perdre les valeurs saisies', async () => {
    cultureService.creer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    const fixture = TestBed.createComponent(CultureFormulaire);
    await fixture.whenStable();
    const nom: HTMLInputElement = fixture.nativeElement.querySelector('#nom');
    nom.value = 'Olivier';
    nom.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'existe déjà',
    );
    expect(nom.value).toBe('Olivier');
    expect(fixture.componentInstance.enregistrement()).toBe(false);
  });

  it('bloque la création de variété si aucune culture n’existe', async () => {
    cultureService.consulterToutes.mockReturnValue(of([]));
    const fixture = TestBed.createComponent(VarieteFormulaire);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('a[href="/cultures/nouvelle"]')).not.toBeNull();
    expect(varieteService.creer).not.toHaveBeenCalled();
  });

  it('préselectionne la culture de la nouvelle variété et enregistre son identifiant', async () => {
    const navigation = await RouterTestingHarness.create();
    const formulaire = await navigation.navigateByUrl(
      '/varietes/nouvelle?cultureId=7',
      VarieteFormulaire,
    );
    expect(formulaire.cultureId).toBe(7);
    const nom = navigation.routeNativeElement!.querySelector<HTMLInputElement>('#nom')!;
    nom.value = ' Chemlali ';
    nom.dispatchEvent(new Event('input'));
    await navigation.fixture.whenStable();
    navigation
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await navigation.fixture.whenStable();
    expect(varieteService.creer).toHaveBeenCalledWith({
      nom: 'Chemlali',
      description: null,
      cultureId: 7,
    });
    expect(TestBed.inject(Router).url).toBe('/varietes?cultureId=7');
  });

  it('transmet le filtre au backend et revient à la première page', async () => {
    const navigation = await RouterTestingHarness.create();
    const liste = await navigation.navigateByUrl('/varietes?cultureId=7', VarieteListe);
    expect(varieteService.consulterPage).toHaveBeenLastCalledWith(0, 10, 7);
    liste.page.set(2);
    const select =
      navigation.routeNativeElement!.querySelector<HTMLSelectElement>('#filtre-culture')!;
    select.value = select.options[2].value;
    select.dispatchEvent(new Event('change'));
    await navigation.fixture.whenStable();
    expect(varieteService.consulterPage).toHaveBeenLastCalledWith(0, 10, 8);
    expect(navigation.routeNativeElement!.textContent).toContain('Aucune variété');
  });

  it('ignore une ancienne réponse quand le filtre de l’URL change', async () => {
    const ancienne = new Subject<PageResponse<VarieteResponse>>();
    varieteService.consulterPage.mockImplementation(
      (page: number, taille: number, cultureId: number | null) =>
        cultureId === 7
          ? ancienne
          : of({ contenu: [], page, taille, totalElements: 0, totalPages: 0 }),
    );
    const navigation = await RouterTestingHarness.create();
    const liste = await navigation.navigateByUrl('/varietes?cultureId=7', VarieteListe);
    await navigation.navigateByUrl('/varietes?cultureId=8');
    ancienne.next({ contenu: [chemlali], page: 0, taille: 10, totalElements: 1, totalPages: 1 });
    ancienne.complete();
    await navigation.fixture.whenStable();
    expect(liste.cultureId).toBe(8);
    expect(liste.varietes()).toEqual([]);
  });

  for (const metier of ['AGRICULTEUR', 'AGRONOME']) {
    it('autorise la lecture et refuse les formulaires pour ' + metier, async () => {
      role = metier;
      const navigation = await RouterTestingHarness.create();
      await navigation.navigateByUrl('/cultures', CultureListe);
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/cultures/nouvelle"]'),
      ).toBeNull();
      await navigation.navigateByUrl('/varietes', VarieteListe);
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/varietes/3/modifier"]'),
      ).toBeNull();
      await navigation.navigateByUrl('/cultures/nouvelle');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
      await navigation.navigateByUrl('/varietes/3/modifier');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
    });
  }
});
