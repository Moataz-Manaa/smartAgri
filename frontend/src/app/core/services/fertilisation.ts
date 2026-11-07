import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FertilisationRequest, FertilisationResponse } from '../models/fertilisation';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class FertilisationService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/fertilisations';

  consulterPage(
    plantationId: number,
    page: number,
    taille: number,
  ): Observable<PageResponse<FertilisationResponse>> {
    const params = new HttpParams()
      .set('plantationId', plantationId)
      .set('page', page)
      .set('taille', taille);
    return this.http.get<PageResponse<FertilisationResponse>>(this.apiUrl + '/pagination', {
      params,
    });
  }

  consulterParId(id: number): Observable<FertilisationResponse> {
    return this.http.get<FertilisationResponse>(this.apiUrl + '/' + id);
  }

  creer(request: FertilisationRequest): Observable<FertilisationResponse> {
    return this.http.post<FertilisationResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: FertilisationRequest): Observable<FertilisationResponse> {
    return this.http.put<FertilisationResponse>(this.apiUrl + '/' + id, request);
  }
}
