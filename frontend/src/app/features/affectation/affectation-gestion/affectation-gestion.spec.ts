import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { NgForm } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AffectationGestion } from './affectation-gestion';
import { AffectationService } from '../../../core/services/affectation';
import { ParcelleService } from '../../../core/services/parcelle';
import { UtilisateurService } from '../../../core/services/utilisateur';
import { AffectationResponse } from '../../../core/models/affectation';
import { UtilisateurResponse } from '../../../core/models/utilisateur';

describe('Gestion des affectations', () => {
  const premiere: AffectationResponse = {
    id: 1,
    utilisateurId: 1,
    utilisateurNom: 'A',
    utilisateurPrenom: 'Test',
    utilisateurEmail: 'a@example.test',
    role: 'AGRICULTEUR',
    parcelleId: 10,
    parcelleCode: 'PAR-01',
    parcelleNom: 'Oliviers',
    exploitationNom: 'Sfax',
  };
  const seconde: AffectationResponse = { ...premiere, id: 2, utilisateurId: 2, role: 'AGRONOME' };
  let service: {
    consulterPage: ReturnType<typeof vi.fn>;
    creer: ReturnType<typeof vi.fn>;
    retirer: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    const utilisateurs: UtilisateurResponse[] = [
      {
        id: 1,
        keycloakId: 'a',
        username: 'a',
        nom: 'A',
        prenom: 'Test',
        email: 'a@example.test',
        role: 'AGRICULTEUR',
        actif: true,
      },
      {
        id: 2,
        keycloakId: 'b',
        username: 'b',
        nom: 'B',
        prenom: 'Test',
        email: 'b@example.test',
        role: 'AGRONOME',
        actif: true,
      },
      {
        id: 3,
        keycloakId: 'c',
        username: 'c',
        nom: 'C',
        prenom: 'Test',
        email: 'c@example.test',
        role: 'ADMIN',
        actif: true,
      },
      {
        id: 4,
        keycloakId: 'd',
        username: 'd',
        nom: 'D',
        prenom: 'Test',
        email: 'd@example.test',
        role: 'AGRICULTEUR',
        actif: false,
      },
    ];
    let affectations = [premiere, seconde];
    service = {
      consulterPage: vi.fn((page: number, taille: number) =>
        of({
          contenu: affectations,
          page,
          taille,
          totalElements: affectations.length,
          totalPages: 1,
        }),
      ),
      creer: vi.fn(() => of({ ...premiere, id: 3 })),
      retirer: vi.fn((id: number) => {
        affectations = affectations.filter((a) => a.id !== id);
        return of(undefined);
      }),
    };
    TestBed.configureTestingModule({
      imports: [AffectationGestion],
      providers: [
        { provide: AffectationService, useValue: service },
        { provide: UtilisateurService, useValue: { consulterTous: () => of(utilisateurs) } },
        {
          provide: ParcelleService,
          useValue: {
            consulterToutes: () =>
              of([
                {
                  id: 10,
                  code: 'PAR-01',
                  nom: 'Oliviers',
                  superficie: 5,
                  typeSol: null,
                  typeIrrigation: null,
                  description: null,
                  exploitationId: 1,
                  exploitationCode: 'EXP-01',
                  exploitationNom: 'Sfax',
                },
              ]),
          },
        },
      ],
    });
  });

  it('propose seulement les agriculteurs et agronomes actifs', async () => {
    const fixture = TestBed.createComponent(AffectationGestion);
    await fixture.whenStable();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#utilisateurId');
    expect(select.options.length).toBe(3); // Le choix vide et les deux comptes éligibles.
    expect(fixture.componentInstance.utilisateurs().map((u) => u.id)).toEqual([1, 2]);
  });

  it('retire une seule affectation et conserve les autres lignes', async () => {
    const fixture = TestBed.createComponent(AffectationGestion);
    await fixture.whenStable();
    const retirer: HTMLButtonElement = fixture.nativeElement.querySelector('tbody button');
    retirer.click();
    await fixture.whenStable();
    expect(service.retirer).toHaveBeenCalledWith(1);
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
    expect(fixture.componentInstance.affectations()[0].id).toBe(2);
  });

  it('conserve le formulaire et la liste quand le backend refuse un doublon', async () => {
    service.creer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    const fixture = TestBed.createComponent(AffectationGestion);
    await fixture.whenStable();
    const parcelle: HTMLSelectElement = fixture.nativeElement.querySelector('#parcelleId');
    const utilisateur: HTMLSelectElement = fixture.nativeElement.querySelector('#utilisateurId');
    parcelle.selectedIndex = 1;
    parcelle.dispatchEvent(new Event('change'));
    utilisateur.selectedIndex = 1;
    utilisateur.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    const formulaire = fixture.debugElement.query(By.directive(NgForm)).injector.get(NgForm);
    fixture.componentInstance.affecter(formulaire);
    await fixture.whenStable();
    expect(service.creer).toHaveBeenCalledWith({ parcelleId: 10, utilisateurId: 1 });
    expect(fixture.componentInstance.affectations().length).toBe(2);
    expect(fixture.componentInstance.utilisateurId).toBe(1);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'existe déjà',
    );
  });

  it('transmet le filtre au backend et revient à la première page', async () => {
    const fixture = TestBed.createComponent(AffectationGestion);
    await fixture.whenStable();
    fixture.componentInstance.page.set(2);
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#filtreParcelleId');
    select.selectedIndex = 1;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(service.consulterPage).toHaveBeenLastCalledWith(0, 10, 10);
    expect(fixture.componentInstance.page()).toBe(0);
  });

  it('revient à la dernière page disponible si la page courante devient vide', async () => {
    const fixture = TestBed.createComponent(AffectationGestion);
    await fixture.whenStable();
    fixture.componentInstance.page.set(1);
    service.consulterPage.mockImplementation((page: number, taille: number) =>
      of({
        contenu: page === 1 ? [] : [seconde],
        page,
        taille,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    fixture.componentInstance.retirer(premiere);
    await fixture.whenStable();
    expect(service.consulterPage).toHaveBeenCalledWith(1, 10, null);
    expect(service.consulterPage).toHaveBeenLastCalledWith(0, 10, null);
    expect(fixture.componentInstance.page()).toBe(0);
    expect(fixture.componentInstance.affectations()).toEqual([seconde]);
  });
});
