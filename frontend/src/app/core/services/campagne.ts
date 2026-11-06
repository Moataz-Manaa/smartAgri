import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CampagneRequest, CampagneResponse, StatutCampagne } from '../models/campagne';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class CampagneService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/campagnes';

  consulterPage(
    page: number,
    taille: number,
    statut: StatutCampagne | null = null,
  ): Observable<PageResponse<CampagneResponse>> {
    let params = new HttpParams().set('page', page).set('taille', taille);
    if (statut !== null) params = params.set('statut', statut);
    return this.http.get<PageResponse<CampagneResponse>>(this.apiUrl + '/pagination', { params });
  }

  consulterToutes(statut: StatutCampagne | null = null): Observable<CampagneResponse[]> {
    let params = new HttpParams();
    if (statut !== null) params = params.set('statut', statut);
    return this.http.get<CampagneResponse[]>(this.apiUrl, { params });
  }

  consulterParId(id: number): Observable<CampagneResponse> {
    return this.http.get<CampagneResponse>(this.apiUrl + '/' + id);
  }

  creer(request: CampagneRequest): Observable<CampagneResponse> {
    return this.http.post<CampagneResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: CampagneRequest): Observable<CampagneResponse> {
    return this.http.put<CampagneResponse>(this.apiUrl + '/' + id, request);
  }

  changerStatut(id: number, statut: StatutCampagne): Observable<CampagneResponse> {
    return this.http.put<CampagneResponse>(this.apiUrl + '/' + id + '/statut', { statut });
  }

  associerParcelles(id: number, parcelleIds: number[]): Observable<CampagneResponse> {
    return this.http.put<CampagneResponse>(this.apiUrl + '/' + id + '/parcelles', { parcelleIds });
  }
}
