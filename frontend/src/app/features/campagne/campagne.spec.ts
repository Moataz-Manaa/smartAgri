import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CampagneFormulaire } from './campagne-formulaire/campagne-formulaire';
import { CampagneDetail } from './campagne-detail/campagne-detail';
import { CampagneListe } from './campagne-liste/campagne-liste';
import { CampagneService } from '../../core/services/campagne';
import { ParcelleService } from '../../core/services/parcelle';
import { AuthService } from '../../core/services/auth';
import { CampagneResponse, StatutCampagne } from '../../core/models/campagne';
import { routes } from '../../app.routes';

describe('Campagnes agricoles', () => {
  let role: string;
  let campagne: CampagneResponse;
  let service: {
    consulterParId: ReturnType<typeof vi.fn>;
    consulterPage: ReturnType<typeof vi.fn>;
    creer: ReturnType<typeof vi.fn>;
    modifier: ReturnType<typeof vi.fn>;
    changerStatut: ReturnType<typeof vi.fn>;
    associerParcelles: ReturnType<typeof vi.fn>;
  };
  let parcelleService: { consulterToutes: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    role = 'ADMIN';
    campagne = {
      id: 7,
      nom: 'Campagne 2027',
      description: null,
      dateDebut: '2027-01-01',
      dateFin: '2027-12-31',
      statut: 'PLANIFIEE',
      parcelleIds: [11],
      parcelles: [
        { id: 11, code: 'PAR-11', nom: 'Oliviers', exploitationId: 1, exploitationNom: 'Sfax' },
      ],
    };
    parcelleService = {
      consulterToutes: vi.fn(() =>
        of([
          {
            id: 11,
            code: 'PAR-11',
            nom: 'Oliviers',
            exploitationId: 1,
            exploitationCode: 'EXP-01',
            exploitationNom: 'Sfax',
            superficie: 5,
            typeSol: null,
            typeIrrigation: null,
            description: null,
          },
          {
            id: 12,
            code: 'PAR-12',
            nom: 'Tomates',
            exploitationId: 2,
            exploitationCode: 'EXP-02',
            exploitationNom: 'Nabeul',
            superficie: 5,
            typeSol: null,
            typeIrrigation: null,
            description: null,
          },
        ]),
      ),
    };
    service = {
      consulterParId: vi.fn(() => of(campagne)),
      consulterPage: vi.fn((page: number, taille: number, statut: StatutCampagne | null) => {
        const contenu = statut === null || campagne.statut === statut ? [campagne] : [];
        return of({
          contenu,
          page,
          taille,
          totalElements: contenu.length,
          totalPages: contenu.length,
        });
      }),
      creer: vi.fn(() => of(campagne)),
      modifier: vi.fn(() => of(campagne)),
      changerStatut: vi.fn((id: number, statut: StatutCampagne) => {
        campagne = { ...campagne, statut };
        return of(campagne);
      }),
      associerParcelles: vi.fn(() => of(campagne)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: { estConnecte: () => true, possedeRole: (valeur: string) => valeur === role },
        },
        { provide: CampagneService, useValue: service },
        { provide: ParcelleService, useValue: parcelleService },
      ],
    });
  });

  function saisir(page: HTMLElement, champ: string, valeur: string): void {
    const input = page.querySelector<HTMLInputElement>('#' + champ)!;
    input.value = valeur;
    input.dispatchEvent(new Event('input'));
  }

  function bouton(page: HTMLElement, texte: string): HTMLButtonElement {
    const resultat = Array.from(page.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === texte,
    );
    if (!resultat) throw new Error('Bouton introuvable : ' + texte);
    return resultat;
  }

  function cocher(page: HTMLElement, id: number, cochee: boolean): void {
    const input = page.querySelector<HTMLInputElement>('#campagne-parcelle-' + id)!;
    input.checked = cochee;
    input.dispatchEvent(new Event('change'));
  }

  it('crée une campagne avec les dates sans imposer un statut depuis le formulaire', async () => {
    const fixture = TestBed.createComponent(CampagneFormulaire);
    await fixture.whenStable();
    saisir(fixture.nativeElement, 'nom', ' Campagne 2027 ');
    saisir(fixture.nativeElement, 'dateDebut', '2027-01-01');
    saisir(fixture.nativeElement, 'dateFin', '2027-12-31');
    cocher(fixture.nativeElement, 11, true);
    await fixture.whenStable();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(service.creer).toHaveBeenCalledWith({
      nom: 'Campagne 2027',
      description: null,
      dateDebut: '2027-01-01',
      dateFin: '2027-12-31',
      parcelleIds: [11],
    });
    expect(TestBed.inject(Router).url).toBe('/campagnes/7');
  });

  it('refuse une date de fin antérieure ou identique au début', async () => {
    const fixture = TestBed.createComponent(CampagneFormulaire);
    await fixture.whenStable();
    saisir(fixture.nativeElement, 'nom', 'Dates');
    saisir(fixture.nativeElement, 'dateDebut', '2027-01-01');
    cocher(fixture.nativeElement, 11, true);
    for (const fin of ['2026-12-31', '2027-01-01']) {
      saisir(fixture.nativeElement, 'dateFin', fin);
      await fixture.whenStable();
      fixture.nativeElement
        .querySelector('form')
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
        'postérieure',
      );
    }
    expect(service.creer).not.toHaveBeenCalled();
  });

  it('conserve une campagne clôturée en lecture seule même via l’URL de modification', async () => {
    campagne.statut = 'CLOTUREE';
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/campagnes/7/modifier', CampagneFormulaire);
    expect(navigation.routeNativeElement!.querySelector('fieldset')!.disabled).toBe(true);
    expect(navigation.routeNativeElement!.querySelector('button[type="submit"]')).toBeNull();
    expect(navigation.routeNativeElement!.textContent).toContain('lecture seule');
    expect(service.modifier).not.toHaveBeenCalled();
  });

  it('démarre une campagne depuis son détail', async () => {
    const navigation = await RouterTestingHarness.create();
    const detail = await navigation.navigateByUrl('/campagnes/7', CampagneDetail);
    bouton(navigation.routeNativeElement!, 'Démarrer la campagne').click();
    await navigation.fixture.whenStable();
    expect(service.changerStatut).toHaveBeenCalledWith(7, 'EN_COURS');
    expect(detail.campagne()?.statut).toBe('EN_COURS');
    expect(navigation.routeNativeElement!.textContent).toContain('La campagne a démarré');
  });

  it('demande une confirmation avant la clôture et permet de l’annuler', async () => {
    campagne.statut = 'EN_COURS';
    const navigation = await RouterTestingHarness.create();
    const detail = await navigation.navigateByUrl('/campagnes/7', CampagneDetail);
    bouton(navigation.routeNativeElement!, 'Clôturer la campagne').click();
    await navigation.fixture.whenStable();
    expect(service.changerStatut).not.toHaveBeenCalled();
    bouton(navigation.routeNativeElement!, 'Annuler').click();
    await navigation.fixture.whenStable();
    expect(detail.confirmationCloture()).toBe(false);
    bouton(navigation.routeNativeElement!, 'Clôturer la campagne').click();
    await navigation.fixture.whenStable();
    bouton(navigation.routeNativeElement!, 'Confirmer la clôture').click();
    await navigation.fixture.whenStable();
    expect(service.changerStatut).toHaveBeenCalledWith(7, 'CLOTUREE');
    expect(
      navigation.routeNativeElement!.querySelector('a[href="/campagnes/7/modifier"]'),
    ).toBeNull();
    expect(detail.confirmationCloture()).toBe(false);
  });

  for (const metier of ['AGRICULTEUR', 'AGRONOME']) {
    it('autorise la consultation et refuse la gestion pour ' + metier, async () => {
      role = metier;
      const navigation = await RouterTestingHarness.create();
      await navigation.navigateByUrl('/campagnes', CampagneListe);
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/campagnes/nouvelle"]'),
      ).toBeNull();
      await navigation.navigateByUrl('/campagnes/7', CampagneDetail);
      expect(
        navigation.routeNativeElement!.querySelector('a[href="/campagnes/7/modifier"]'),
      ).toBeNull();
      expect(navigation.routeNativeElement!.textContent).not.toContain('Démarrer la campagne');
      await navigation.navigateByUrl('/campagnes/nouvelle');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
      await navigation.navigateByUrl('/campagnes/7/modifier');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
      expect(service.changerStatut).not.toHaveBeenCalled();
    });
  }

  it('filtre par statut côté backend et revient à la première page', async () => {
    const navigation = await RouterTestingHarness.create();
    const liste = await navigation.navigateByUrl('/campagnes', CampagneListe);
    liste.page.set(2);
    const select =
      navigation.routeNativeElement!.querySelector<HTMLSelectElement>('#filtre-statut')!;
    select.value = 'EN_COURS';
    select.dispatchEvent(new Event('change'));
    await navigation.fixture.whenStable();
    expect(service.consulterPage).toHaveBeenLastCalledWith(0, 10, 'EN_COURS');
    expect(navigation.routeNativeElement!.textContent).toContain('Aucune campagne');
  });

  it('affiche un conflit et retrouve l’état réel à la réouverture du détail', async () => {
    service.changerStatut.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'Le statut a changé. Actualisez la campagne.' },
          }),
      ),
    );
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/campagnes/7', CampagneDetail);
    bouton(navigation.routeNativeElement!, 'Démarrer la campagne').click();
    await navigation.fixture.whenStable();
    expect(navigation.routeNativeElement!.querySelector('[role="alert"]')!.textContent).toContain(
      'Actualisez',
    );
    campagne.statut = 'CLOTUREE';
    await navigation.navigateByUrl('/campagnes', CampagneListe);
    await navigation.navigateByUrl('/campagnes/7', CampagneDetail);
    await navigation.fixture.whenStable();
    expect(navigation.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
    expect(navigation.routeNativeElement!.textContent).not.toContain('Démarrer la campagne');
  });

  it('exige au moins une parcelle avant la création', async () => {
    const fixture = TestBed.createComponent(CampagneFormulaire);
    await fixture.whenStable();
    saisir(fixture.nativeElement, 'nom', 'Sélection');
    saisir(fixture.nativeElement, 'dateDebut', '2027-01-01');
    saisir(fixture.nativeElement, 'dateFin', '2027-12-31');
    await fixture.whenStable();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(service.creer).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'au moins une parcelle',
    );
  });

  it('permet de sélectionner plusieurs parcelles et de retirer une sélection', async () => {
    const fixture = TestBed.createComponent(CampagneFormulaire);
    await fixture.whenStable();
    cocher(fixture.nativeElement, 11, true);
    cocher(fixture.nativeElement, 12, true);
    expect(fixture.componentInstance.parcelleIds).toEqual([11, 12]);
    cocher(fixture.nativeElement, 11, false);
    expect(fixture.componentInstance.parcelleIds).toEqual([12]);
  });

  it('préselectionne les parcelles à la modification et enregistre leur changement', async () => {
    campagne.parcelleIds = [11, 12];
    campagne.parcelles.push({
      id: 12,
      code: 'PAR-12',
      nom: 'Tomates',
      exploitationId: 2,
      exploitationNom: 'Nabeul',
    });
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/campagnes/7/modifier', CampagneFormulaire);
    expect(
      navigation.routeNativeElement!.querySelector<HTMLInputElement>('#campagne-parcelle-11')!
        .checked,
    ).toBe(true);
    expect(
      navigation.routeNativeElement!.querySelector<HTMLInputElement>('#campagne-parcelle-12')!
        .checked,
    ).toBe(true);
    cocher(navigation.routeNativeElement!, 12, false);
    await navigation.fixture.whenStable();
    navigation
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await navigation.fixture.whenStable();
    expect(service.modifier).toHaveBeenCalledWith(7, {
      nom: 'Campagne 2027',
      description: null,
      dateDebut: '2027-01-01',
      dateFin: '2027-12-31',
      parcelleIds: [11],
    });
    expect(service.creer).not.toHaveBeenCalled();
  });

  it('rattache les parcelles initiales d’une ancienne campagne clôturée sans modifier ses informations', async () => {
    campagne.statut = 'CLOTUREE';
    campagne.parcelleIds = [];
    campagne.parcelles = [];
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/campagnes/7/modifier', CampagneFormulaire);
    expect(
      navigation.routeNativeElement!.querySelector<HTMLInputElement>('#nom')!.matches(':disabled'),
    ).toBe(true);
    expect(
      navigation.routeNativeElement!.querySelector<HTMLInputElement>('#campagne-parcelle-11')!
        .disabled,
    ).toBe(false);
    cocher(navigation.routeNativeElement!, 11, true);
    await navigation.fixture.whenStable();
    navigation
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await navigation.fixture.whenStable();
    expect(service.associerParcelles).toHaveBeenCalledWith(7, [11]);
    expect(service.modifier).not.toHaveBeenCalled();
    expect(service.changerStatut).not.toHaveBeenCalled();
  });

  it('bloque la création en l’absence de parcelles disponibles', async () => {
    parcelleService.consulterToutes.mockReturnValue(of([]));
    const fixture = TestBed.createComponent(CampagneFormulaire);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('a[href="/parcelles/nouvelle"]')).not.toBeNull();
  });

  it('présente uniquement les parcelles fournies par la réponse filtrée pour un agriculteur', async () => {
    role = 'AGRICULTEUR';
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/campagnes/7', CampagneDetail);
    expect(navigation.routeNativeElement!.querySelector('a[href="/parcelles/11"]')).not.toBeNull();
    expect(navigation.routeNativeElement!.querySelector('a[href="/parcelles/12"]')).toBeNull();
    expect(navigation.routeNativeElement!.textContent).not.toContain('Nabeul');
    expect(navigation.routeNativeElement!.textContent).not.toContain('Tomates');
    expect(parcelleService.consulterToutes).not.toHaveBeenCalled();
  });

  it('n’affiche pas de campagne ni de parcelles lorsque le backend refuse l’accès direct', async () => {
    role = 'AGRONOME';
    service.consulterParId.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/campagnes/7', CampagneDetail);
    expect(navigation.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();
    expect(navigation.routeNativeElement!.querySelector('h1')).toBeNull();
    expect(navigation.routeNativeElement!.querySelector('a[href="/parcelles/11"]')).toBeNull();
  });

  it('bloque les doubles changements pendant une requête', async () => {
    const reponse = new Subject<CampagneResponse>();
    service.changerStatut.mockReturnValue(reponse);
    const navigation = await RouterTestingHarness.create();
    const detail = await navigation.navigateByUrl('/campagnes/7', CampagneDetail);
    bouton(navigation.routeNativeElement!, 'Démarrer la campagne').click();
    await navigation.fixture.whenStable();
    detail.demarrer();
    expect(service.changerStatut).toHaveBeenCalledTimes(1);
    expect(detail.enregistrement()).toBe(true);
    reponse.next({ ...campagne, statut: 'EN_COURS' });
    reponse.complete();
    await navigation.fixture.whenStable();
    expect(detail.enregistrement()).toBe(false);
  });
});
