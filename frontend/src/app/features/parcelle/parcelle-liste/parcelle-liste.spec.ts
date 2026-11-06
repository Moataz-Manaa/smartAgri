import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ParcelleListe } from './parcelle-liste';
import { ParcelleService } from '../../../core/services/parcelle';
import { AuthService } from '../../../core/services/auth';
import { ParcelleResponse } from '../../../core/models/parcelle';

describe('Liste paginée des parcelles', () => {
  it('charge les pages depuis le backend et revient à la première lors du changement de taille', async () => {
    const parcelle: ParcelleResponse = {
      id: 1,
      code: 'PAR-01',
      nom: 'Oliviers',
      superficie: 5,
      typeSol: null,
      typeIrrigation: null,
      description: null,
      exploitationId: 1,
      exploitationCode: 'EXP-01',
      exploitationNom: 'Sfax',
    };
    const service = {
      consulterPage: vi.fn((page: number, taille: number) =>
        of({
          contenu: [{ ...parcelle, id: page + 1, code: 'PAR-0' + (page + 1) }],
          page,
          taille,
          totalElements: 11,
          totalPages: taille === 10 ? 2 : 1,
        }),
      ),
      consulterToutes: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [ParcelleListe],
      providers: [
        provideRouter([]),
        { provide: ParcelleService, useValue: service },
        { provide: AuthService, useValue: { possedeRole: () => false } },
      ],
    });
    const fixture = TestBed.createComponent(ParcelleListe);
    await fixture.whenStable();
    expect(service.consulterPage).toHaveBeenLastCalledWith(0, 10);
    let boutons: NodeListOf<HTMLButtonElement> =
      fixture.nativeElement.querySelectorAll('app-pagination button');
    boutons[1].click();
    await fixture.whenStable();
    expect(service.consulterPage).toHaveBeenLastCalledWith(1, 10);
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain('PAR-02');
    expect(fixture.nativeElement.querySelector('tbody').textContent).not.toContain('PAR-01');
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('app-pagination select');
    select.value = '20';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(service.consulterPage).toHaveBeenLastCalledWith(0, 20);
    expect(fixture.componentInstance.page()).toBe(0);
    expect(service.consulterToutes).not.toHaveBeenCalled();
  });
});
