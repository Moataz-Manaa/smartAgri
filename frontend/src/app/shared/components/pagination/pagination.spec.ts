import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Pagination } from './pagination';

describe('Pagination', () => {
  it('demande la page suivante et bloque les clics pendant le chargement', async () => {
    const fixture = TestBed.createComponent(Pagination);
    fixture.componentRef.setInput('totalPages', 3);
    fixture.componentRef.setInput('totalElements', 25);
    const changement = vi.fn();
    fixture.componentInstance.pageChange.subscribe(changement);
    await fixture.whenStable();
    const boutons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button');
    expect(boutons[0].disabled).toBe(true);
    boutons[1].click();
    expect(changement).toHaveBeenCalledWith(1);
    changement.mockClear();
    fixture.componentRef.setInput('desactive', true);
    await fixture.whenStable();
    boutons[1].click();
    expect(changement).not.toHaveBeenCalled();
  });

  it('bloque la page suivante sur la dernière page et les deux boutons sans résultat', async () => {
    const fixture = TestBed.createComponent(Pagination);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('totalPages', 2);
    await fixture.whenStable();
    const boutons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button');
    expect(boutons[0].disabled).toBe(false);
    expect(boutons[1].disabled).toBe(true);
    fixture.componentRef.setInput('page', 0);
    fixture.componentRef.setInput('totalPages', 0);
    await fixture.whenStable();
    expect(boutons[0].disabled).toBe(true);
    expect(boutons[1].disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Page 0 / 0');
  });

  it('demande la taille sélectionnée', async () => {
    const fixture = TestBed.createComponent(Pagination);
    const changement = vi.fn();
    fixture.componentInstance.tailleChange.subscribe(changement);
    await fixture.whenStable();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = '20';
    select.dispatchEvent(new Event('change'));
    expect(changement).toHaveBeenCalledWith(20);
  });
});
