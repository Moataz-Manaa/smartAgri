import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/page';

import {
  UtilisateurCreationRequest,
  UtilisateurModificationRequest,
  UtilisateurResponse,
} from '../models/utilisateur';

@Injectable({
  providedIn: 'root',
})
export class UtilisateurService {
  consulterPage(page: number, taille: number): Observable<PageResponse<UtilisateurResponse>> {
    const params = { page, taille };
    return this.http.get<PageResponse<UtilisateurResponse>>(this.apiUrl + '/pagination', {
      params,
    });
  }

  private http = inject(HttpClient);

  private apiUrl = 'http://localhost:8081/api/utilisateurs';

  consulterTous(): Observable<UtilisateurResponse[]> {
    return this.http.get<UtilisateurResponse[]>(this.apiUrl);
  }

  consulterParId(id: number): Observable<UtilisateurResponse> {
    return this.http.get<UtilisateurResponse>(this.apiUrl + '/' + id);
  }

  creer(request: UtilisateurCreationRequest): Observable<UtilisateurResponse> {
    return this.http.post<UtilisateurResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: UtilisateurModificationRequest): Observable<UtilisateurResponse> {
    return this.http.put<UtilisateurResponse>(this.apiUrl + '/' + id, request);
  }

  activer(id: number): Observable<UtilisateurResponse> {
    return this.http.put<UtilisateurResponse>(this.apiUrl + '/' + id + '/activer', null);
  }

  desactiver(id: number): Observable<UtilisateurResponse> {
    return this.http.put<UtilisateurResponse>(this.apiUrl + '/' + id + '/desactiver', null);
  }
}
