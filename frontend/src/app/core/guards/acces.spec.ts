import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { AuthService } from '../services/auth';
import { lectureGuard } from './lecture-guard';
import { adminGuard } from './admin-guard';
import { routes } from '../../app.routes';

describe('Accès aux pages', () => {
  let role: string;
  let connecte: boolean;
  beforeEach(() => {
    role = 'AGRICULTEUR';
    connecte = true;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            estConnecte: () => connecte,
            possedeRole: (valeur: string) => valeur === role,
          },
        },
      ],
    });
  });

  it('autorise la lecture pour les trois rôles', () => {
    for (role of ['ADMIN', 'AGRICULTEUR', 'AGRONOME']) {
      expect(
        TestBed.runInInjectionContext(() =>
          lectureGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
        ),
      ).toBe(true);
    }
  });

  it('refuse la gestion aux agriculteurs et agronomes', () => {
    for (role of ['AGRICULTEUR', 'AGRONOME']) {
      expect(
        TestBed.runInInjectionContext(() =>
          adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
        ),
      ).toBeInstanceOf(UrlTree);
    }
  });

  it('refuse la lecture sans connexion et sans rôle métier', () => {
    connecte = false;
    expect(
      TestBed.runInInjectionContext(() =>
        lectureGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
      ),
    ).toBeInstanceOf(UrlTree);
    connecte = true;
    role = 'AUTRE';
    expect(
      TestBed.runInInjectionContext(() =>
        lectureGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
      ),
    ).toBeInstanceOf(UrlTree);
  });

  it('reconnaît les formulaires avant les routes de détail', () => {
    expect(routes.findIndex((r) => r.path === 'parcelles/nouvelle')).toBeLessThan(
      routes.findIndex((r) => r.path === 'parcelles/:id'),
    );
    expect(routes.findIndex((r) => r.path === 'exploitations/nouvelle')).toBeLessThan(
      routes.findIndex((r) => r.path === 'exploitations/:id'),
    );
  });
});
