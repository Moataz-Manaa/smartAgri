import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { provideRouter } from '@angular/router';
import { AuthService } from './core/services/auth';

describe('App', () => {
  let role = 'ADMIN';
  let connecte = true;
  beforeEach(async () => {
    role = 'ADMIN';
    connecte = true;
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            estConnecte: () => connecte,
            possedeRole: (valeur: string) => valeur === role,
            obtenirNomUtilisateur: () => 'Compte de test',
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('affiche la gestion des affectations pour un administrateur', async () => {
    role = 'ADMIN';
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('a[href="/affectations"]')).not.toBeNull();
    expect(compiled.querySelector('a[href="/utilisateurs"]')).not.toBeNull();
    expect(compiled.querySelector('a[href="/accueil"]')).not.toBeNull();
  });

  for (const metier of ['AGRICULTEUR', 'AGRONOME']) {
    it('affiche Mes parcelles sans la gestion administrative pour ' + metier, async () => {
      role = metier;
      const fixture = TestBed.createComponent(App);
      await fixture.whenStable();
      const page: HTMLElement = fixture.nativeElement;
      expect(page.querySelector('a[href="/parcelles"]')?.textContent).toContain('Mes parcelles');
      expect(page.querySelector('a[href="/affectations"]')).toBeNull();
      expect(page.querySelector('a[href="/accueil"]')).not.toBeNull();
      expect(page.querySelector('a[href="/utilisateurs"]')).toBeNull();
    });
  }

  it('masque la navigation avant connexion', async () => {
    connecte = false;
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-sidebar')).toBeNull();
    connecte = true;
  });
});
