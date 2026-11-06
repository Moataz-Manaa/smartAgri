import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';

import { AuthService } from '../services/auth';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);

  const apiUrl = 'http://localhost:8081/api/';

  if (!request.url.startsWith(apiUrl)) {
    return next(request);
  }

  return from(authService.obtenirToken()).pipe(
    switchMap((token) => {
      if (token === null) {
        return next(request);
      }

      const requestAvecToken = request.clone({
        setHeaders: {
          Authorization: 'Bearer ' + token,
        },
      });

      return next(requestAvecToken);
    }),
  );
};
