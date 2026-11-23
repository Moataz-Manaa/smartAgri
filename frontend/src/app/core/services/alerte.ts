import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AlerteFiltres, AlerteResponse, CompteurAlertesResponse } from '../models/alerte';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class AlerteService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/alertes';

  consulterPage(
    page: number,
    taille: number,
    filtres: AlerteFiltres,
  ): Observable<PageResponse<AlerteResponse>> {
    let params = new HttpParams().set('page', page).set('taille', taille);
    if (filtres.parcelleId !== null) params = params.set('parcelleId', filtres.parcelleId);
    if (filtres.type) params = params.set('type', filtres.type);
    if (filtres.niveau) params = params.set('niveau', filtres.niveau);
    if (filtres.statut) params = params.set('statut', filtres.statut);
    if (filtres.lue !== null) params = params.set('lue', filtres.lue);
    if (filtres.dateDebut) params = params.set('dateDebut', filtres.dateDebut);
    if (filtres.dateFin) params = params.set('dateFin', filtres.dateFin);
    return this.http.get<PageResponse<AlerteResponse>>(this.apiUrl + '/pagination', { params });
  }

  consulterParId(id: number): Observable<AlerteResponse> {
    return this.http.get<AlerteResponse>(this.apiUrl + '/' + id);
  }

  consulterCompteur(parcelleId: number | null): Observable<CompteurAlertesResponse> {
    let params = new HttpParams();
    if (parcelleId !== null) params = params.set('parcelleId', parcelleId);
    return this.http.get<CompteurAlertesResponse>(this.apiUrl + '/compteur', { params });
  }

  marquerLue(id: number): Observable<AlerteResponse> {
    return this.http.put<AlerteResponse>(this.apiUrl + '/' + id + '/lecture', {});
  }
}
