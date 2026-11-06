import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Accueil } from './accueil';
import { AuthService } from '../../core/services/auth';
import { ExploitationService } from '../../core/services/exploitation';
import { ParcelleService } from '../../core/services/parcelle';
import { UtilisateurService } from '../../core/services/utilisateur';
import { AffectationService } from '../../core/services/affectation';
import { PageResponse } from '../../core/models/page';
import { ParcelleResponse } from '../../core/models/parcelle';
import { routes } from '../../app.routes';

describe('Accueil selon le rôle', () => {
  let role: string;
  let connecte: boolean;
  const page = (total: number) => ({
    contenu: [],
    page: 0,
    taille: 1,
    totalElements: total,
    totalPages: total,
  });
  let exploitations: { consulterPage: ReturnType<typeof vi.fn> };
  let parcelles: { consulterPage: ReturnType<typeof vi.fn> };
  let utilisateurs: { consulterPage: ReturnType<typeof vi.fn> };
  let affectations: { consulterPage: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    role = 'ADMIN';
    connecte = true;
    exploitations = { consulterPage: vi.fn(() => of(page(2))) };
    parcelles = { consulterPage: vi.fn(() => of(page(3))) };
    utilisateurs = { consulterPage: vi.fn(() => of(page(4))) };
    affectations = { consulterPage: vi.fn(() => of(page(7))) };
    TestBed.configureTestingModule({
      imports: [Accueil],
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: {
            estConnecte: () => connecte,
            possedeRole: (valeur: string) => valeur === role,
            obtenirNomUtilisateur: () => 'Compte test',
          },
        },
        { provide: ExploitationService, useValue: exploitations },
        { provide: ParcelleService, useValue: parcelles },
        { provide: UtilisateurService, useValue: utilisateurs },
        { provide: AffectationService, useValue: affectations },
      ],
    });
  });

  it('affiche les totaux et les raccourcis de gestion pour un administrateur', async () => {
    const fixture = TestBed.createComponent(Accueil);
    await fixture.whenStable();
    const pageAccueil: HTMLElement = fixture.nativeElement;
    expect(pageAccueil.querySelector('h1')?.textContent).toContain('Compte test');
    expect(
      pageAccueil.querySelector('[data-testid="nombre-exploitations"] .nombre')?.textContent,
    ).toBe('2');
    expect(pageAccueil.querySelector('[data-testid="nombre-parcelles"] .nombre')?.textContent).toBe(
      '3',
    );
    expect(
      pageAccueil.querySelector('[data-testid="nombre-utilisateurs"] .nombre')?.textContent,
    ).toBe('4');
    expect(
      pageAccueil.querySelector('[data-testid="nombre-affectations"] .nombre')?.textContent,
    ).toBe('7');
    for (const lien of [
      '/exploitations/nouvelle',
      '/parcelles/nouvelle',
      '/utilisateurs/nouveau',
      '/affectations',
    ]) {
      expect(pageAccueil.querySelector('a[href="' + lien + '"]')).not.toBeNull();
    }
    expect(utilisateurs.consulterPage).toHaveBeenCalledWith(0, 1);
    expect(affectations.consulterPage).toHaveBeenCalledWith(0, 1);
  });

  for (const metier of ['AGRICULTEUR', 'AGRONOME']) {
    it('limite les appels et liens aux données accessibles pour ' + metier, async () => {
      role = metier;
      const fixture = TestBed.createComponent(Accueil);
      await fixture.whenStable();
      const pageAccueil: HTMLElement = fixture.nativeElement;
      expect(pageAccueil.textContent).toContain(
        metier === 'AGRONOME' ? 'Ingénieur agronome' : 'Agriculteur',
      );
      expect(pageAccueil.querySelectorAll('.carte').length).toBe(2);
      expect(pageAccueil.querySelector('a[href="/parcelles"]')).not.toBeNull();
      expect(pageAccueil.querySelector('a[href="/exploitations"]')).not.toBeNull();
      expect(pageAccueil.querySelector('a[href="/utilisateurs"]')).toBeNull();
      expect(pageAccueil.querySelector('a[href="/affectations"]')).toBeNull();
      expect(pageAccueil.querySelector('a[href="/parcelles/nouvelle"]')).toBeNull();
      expect(utilisateurs.consulterPage).not.toHaveBeenCalled();
      expect(affectations.consulterPage).not.toHaveBeenCalled();
    });
  }

  it('explique comment obtenir un accès quand aucune parcelle n’est affectée', async () => {
    role = 'AGRICULTEUR';
    parcelles.consulterPage.mockReturnValue(of(page(0)));
    exploitations.consulterPage.mockReturnValue(of(page(0)));
    const fixture = TestBed.createComponent(Accueil);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(
      'Aucune parcelle ne vous est encore affectée',
    );
    expect(
      fixture.nativeElement.querySelector('[data-testid="nombre-parcelles"] .nombre').textContent,
    ).toBe('0');
  });

  it('affiche une erreur sans inventer de totaux et recharge à la réouverture', async () => {
    parcelles.consulterPage.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    const fixture = TestBed.createComponent(Accueil);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Impossible de charger',
    );
    expect(
      fixture.nativeElement.querySelector('[data-testid="nombre-parcelles"] .nombre').textContent,
    ).toBe('—');
    expect(fixture.componentInstance.nombreExploitations()).toBeNull();
    parcelles.consulterPage.mockReturnValue(of(page(3)));
    fixture.destroy();
    const nouvelleOuverture = TestBed.createComponent(Accueil);
    await nouvelleOuverture.whenStable();
    expect(nouvelleOuverture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(nouvelleOuverture.componentInstance.nombreParcelles()).toBe(3);
  });

  it('évite les chargements simultanés des indicateurs', async () => {
    const reponse = new Subject<PageResponse<ParcelleResponse>>();
    parcelles.consulterPage.mockReturnValue(reponse);
    const fixture = TestBed.createComponent(Accueil);
    await fixture.whenStable();
    expect(fixture.componentInstance.chargement()).toBe(true);
    expect(fixture.componentInstance.nombreParcelles()).toBeNull();
    fixture.componentInstance.chargerIndicateurs();
    expect(parcelles.consulterPage).toHaveBeenCalledTimes(1);
    reponse.next(page(3));
    reponse.complete();
    await fixture.whenStable();
    expect(fixture.componentInstance.chargement()).toBe(false);
    expect(fixture.componentInstance.nombreParcelles()).toBe(3);
  });

  it('ouvre l’accueil depuis la racine après connexion', async () => {
    const navigation = await RouterTestingHarness.create();
    const accueil = await navigation.navigateByUrl('/', Accueil);
    expect(accueil).toBeInstanceOf(Accueil);
    expect(TestBed.inject(Router).url).toBe('/accueil');
  });

  for (const cas of ['deconnecte', 'sans-role']) {
    it('refuse l’accueil pour un compte ' + cas, async () => {
      connecte = cas !== 'deconnecte';
      role = 'AUTRE';
      const navigation = await RouterTestingHarness.create();
      await navigation.navigateByUrl('/accueil');
      expect(TestBed.inject(Router).url).toBe('/acces-refuse');
      expect(parcelles.consulterPage).not.toHaveBeenCalled();
      expect(utilisateurs.consulterPage).not.toHaveBeenCalled();
    });
  }
});
