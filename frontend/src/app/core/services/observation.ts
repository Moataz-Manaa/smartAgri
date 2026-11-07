import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ObservationRequest, ObservationResponse } from '../models/observation';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class ObservationService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/observations';

  consulterPage(
    plantationId: number,
    page: number,
    taille: number,
  ): Observable<PageResponse<ObservationResponse>> {
    const params = new HttpParams()
      .set('plantationId', plantationId)
      .set('page', page)
      .set('taille', taille);
    return this.http.get<PageResponse<ObservationResponse>>(this.apiUrl + '/pagination', {
      params,
    });
  }

  consulterParId(id: number): Observable<ObservationResponse> {
    return this.http.get<ObservationResponse>(this.apiUrl + '/' + id);
  }

  creer(request: ObservationRequest): Observable<ObservationResponse> {
    return this.http.post<ObservationResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: ObservationRequest): Observable<ObservationResponse> {
    return this.http.put<ObservationResponse>(this.apiUrl + '/' + id, request);
  }
}
