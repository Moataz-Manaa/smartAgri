import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth';

export const agriculteurGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.estConnecte() && authService.possedeRole('AGRICULTEUR')) return true;
  return router.createUrlTree(['/acces-refuse']);
};
