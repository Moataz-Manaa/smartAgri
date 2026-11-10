import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HistoriqueService } from '../../core/services/historique';
import { ActiviteResponse } from '../../core/models/historique';
import { PageResponse } from '../../core/models/page';
import { HistoriqueListe } from './historique-liste';

describe('Historique agricole', () => {
  const observation: ActiviteResponse = {
    id: 1,
    type: 'OBSERVATION',
    date: '2020-06-03',
    description: 'Feuilles jaunes',
    nomProduit: null,
    quantite: null,
    unite: null,
    auteurNom: 'Ali',
    auteurPrenom: 'Ahmed',
  };
  const recolte: ActiviteResponse = {
    ...observation,
    type: 'RECOLTE',
    description: null,
    quantite: 2,
    unite: 'TONNE',
  };
  let service: { consulterPage: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    service = {
      consulterPage: vi.fn(() =>
        of({
          contenu: [observation, recolte],
          page: 0,
          taille: 10,
          totalElements: 2,
          totalPages: 1,
        }),
      ),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: HistoriqueService, useValue: service }],
    });
  });

  async function ouvrir() {
    const fixture = TestBed.createComponent(HistoriqueListe);
    fixture.componentRef.setInput('plantationId', 9);
    await fixture.whenStable();
    return fixture;
  }

  it('affiche des types partageant le même identifiant sans perdre une activité', async () => {
    const fixture = await ouvrir();
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Feuilles jaunes');
    expect(fixture.nativeElement.textContent).toContain('Récolte');
    expect(fixture.nativeElement.textContent).toContain('2 t');
    expect(fixture.nativeElement.textContent).toContain('Ahmed Ali');
  });

  it('transmet les filtres du formulaire et revient à la première page', async () => {
    const fixture = await ouvrir();
    const type: HTMLSelectElement = fixture.nativeElement.querySelector('select[name="type"]');
    type.value = 'RECOLTE';
    type.dispatchEvent(new Event('change'));
    for (const name of ['dateDebut', 'dateFin']) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(`input[name="${name}"]`);
      input.value = '2020-06-03';
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
    fixture.componentInstance.page.set(4);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(service.consulterPage).toHaveBeenLastCalledWith(
      9,
      0,
      10,
      'RECOLTE',
      '2020-06-03',
      '2020-06-03',
    );
  });

  it('refuse les dates inversées et efface les anciennes activités', async () => {
    const fixture = await ouvrir();
    fixture.componentInstance.dateDebut = '2020-07-01';
    fixture.componentInstance.dateFin = '2020-06-01';
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(service.consulterPage).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.activites()).toEqual([]);
    expect(fixture.componentInstance.totalElements()).toBe(0);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'date de début',
    );
  });

  it('réinitialise les filtres et la page', async () => {
    const fixture = await ouvrir();
    fixture.componentInstance.type = 'RECOLTE';
    fixture.componentInstance.dateDebut = '2020-01-01';
    fixture.componentInstance.dateFin = '2020-12-31';
    fixture.componentInstance.reinitialiser();
    expect(service.consulterPage).toHaveBeenLastCalledWith(9, 0, 10, '', '', '');
  });

  it('efface les activités et leur nombre si l’accès est retiré', async () => {
    const fixture = await ouvrir();
    service.consulterPage.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    fixture.componentInstance.charger();
    await fixture.whenStable();
    expect(fixture.componentInstance.activites()).toEqual([]);
    expect(fixture.componentInstance.totalElements()).toBe(0);
    expect(fixture.componentInstance.totalPages()).toBe(0);
    expect(fixture.nativeElement.textContent).not.toContain('Feuilles jaunes');
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('droits');
  });

  it('annule les requêtes précédentes pendant la pagination et à la destruction', async () => {
    const ancienne = new Subject<PageResponse<ActiviteResponse>>();
    service.consulterPage.mockReturnValueOnce(ancienne);
    const fixture = await ouvrir();
    fixture.componentInstance.changerTaille(20);
    expect(ancienne.observed).toBe(false);
    expect(service.consulterPage).toHaveBeenLastCalledWith(9, 0, 20, '', '', '');
    fixture.componentInstance.charger(1);
    expect(service.consulterPage).toHaveBeenLastCalledWith(9, 1, 20, '', '', '');
    service.consulterPage.mockReturnValue(ancienne);
    fixture.componentInstance.charger();
    expect(ancienne.observed).toBe(true);
    fixture.destroy();
    expect(ancienne.observed).toBe(false);
  });

  it('présente un état vide après filtrage', async () => {
    service.consulterPage.mockReturnValue(
      of({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 }),
    );
    const fixture = await ouvrir();
    expect(fixture.nativeElement.textContent).toContain('Aucune activité pour cette sélection.');
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(0);
  });
});

describe('Requête HTTP de l’historique', () => {
  it('encode les filtres et omet les valeurs vides', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(HistoriqueService);
    const http = TestBed.inject(HttpTestingController);
    service.consulterPage(9, 2, 20, 'RECOLTE', '2020-06-01', '').subscribe();
    const requete = http.expectOne(
      (r) => r.url === 'http://localhost:8081/api/historique/pagination',
    );
    expect(requete.request.params.get('plantationId')).toBe('9');
    expect(requete.request.params.get('page')).toBe('2');
    expect(requete.request.params.get('taille')).toBe('20');
    expect(requete.request.params.get('type')).toBe('RECOLTE');
    expect(requete.request.params.get('dateDebut')).toBe('2020-06-01');
    expect(requete.request.params.has('dateFin')).toBe(false);
    requete.flush({ contenu: [], page: 2, taille: 20, totalElements: 0, totalPages: 0 });
    http.verify();
  });
});
