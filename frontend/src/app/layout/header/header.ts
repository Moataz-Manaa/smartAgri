import { afterNextRender, Component, inject, signal } from '@angular/core';

import { AuthService } from '../../core/services/auth';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-header',
  imports: [RouterLink],
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class Header {
  authService = inject(AuthService);

  pret = signal(false);

  erreur = signal('');

  constructor() {
    afterNextRender(() => {
      this.pret.set(true);
    });
  }

  async connecter(): Promise<void> {
    this.erreur.set('');

    try {
      await this.authService.connecter();
    } catch {
      this.erreur.set('La connexion a échoué. Réessaie.');
    }
  }

  async deconnecter(): Promise<void> {
    this.erreur.set('');

    try {
      await this.authService.deconnecter();
    } catch {
      this.erreur.set('La déconnexion a échoué. Réessaie.');
    }
  }
}
