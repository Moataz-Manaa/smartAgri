import { isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import type Keycloak from 'keycloak-js';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private platformId = inject(PLATFORM_ID);

  private keycloak: Keycloak | null = null;

  async initialiser(): Promise<void> {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const moduleKeycloak = await import('keycloak-js');

    this.keycloak = new moduleKeycloak.default({
      url: 'http://127.0.0.1:8082',
      realm: 'smartagri',
      clientId: 'smartagri-angular',
    });

    await this.keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
  }

  async connecter(): Promise<void> {
    if (this.keycloak === null) {
      throw new Error('Keycloak n’est pas initialisé');
    }

    await this.keycloak.login({
      redirectUri: window.location.origin,
    });
  }

  async deconnecter(): Promise<void> {
    if (this.keycloak === null) {
      throw new Error('Keycloak n’est pas initialisé');
    }

    await this.keycloak.logout({
      redirectUri: window.location.origin,
    });
  }

  estConnecte(): boolean {
    return this.keycloak?.authenticated === true;
  }

  obtenirNomUtilisateur(): string {
    return this.keycloak?.tokenParsed?.['preferred_username'] ?? '';
  }

  possedeRole(role: string): boolean {
    return this.keycloak?.hasRealmRole(role) === true;
  }

  async obtenirToken(): Promise<string | null> {
    if (this.keycloak === null || !this.keycloak.authenticated) {
      return null;
    }

    try {
      await this.keycloak.updateToken(30);

      return this.keycloak.token ?? null;
    } catch {
      this.keycloak.clearToken();

      throw new Error('La session a expiré. Connecte-toi à nouveau.');
    }
  }
}
