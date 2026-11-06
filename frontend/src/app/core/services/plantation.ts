import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/page';
import { PlantationRequest, PlantationResponse, StatutPlantation } from '../models/plantation';

@Injectable({ providedIn: 'root' })
export class PlantationService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/plantations';

  consulterPage(
    page: number,
    taille: number,
    parcelleId: number | null = null,
    campagneId: number | null = null,
    statut: StatutPlantation | null = null,
  ): Observable<PageResponse<PlantationResponse>> {
    let params = new HttpParams().set('page', page).set('taille', taille);
    if (parcelleId !== null) params = params.set('parcelleId', parcelleId);
    if (campagneId !== null) params = params.set('campagneId', campagneId);
    if (statut !== null) params = params.set('statut', statut);
    return this.http.get<PageResponse<PlantationResponse>>(this.apiUrl + '/pagination', { params });
  }

  consulterParId(id: number): Observable<PlantationResponse> {
    return this.http.get<PlantationResponse>(this.apiUrl + '/' + id);
  }

  creer(request: PlantationRequest): Observable<PlantationResponse> {
    return this.http.post<PlantationResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: PlantationRequest): Observable<PlantationResponse> {
    return this.http.put<PlantationResponse>(this.apiUrl + '/' + id, request);
  }
}
