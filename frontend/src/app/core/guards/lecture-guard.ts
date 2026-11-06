import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth';

export const lectureGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (
    authService.estConnecte() &&
    (authService.possedeRole('ADMIN') ||
      authService.possedeRole('AGRICULTEUR') ||
      authService.possedeRole('AGRONOME'))
  ) {
    return true;
  }
  return router.createUrlTree(['/acces-refuse']);
};
