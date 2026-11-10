import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RecolteRequest, RecolteResponse, TotalRecolteResponse } from '../models/recolte';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class RecolteService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/recoltes';

  consulterPage(
    plantationId: number,
    page: number,
    taille: number,
  ): Observable<PageResponse<RecolteResponse>> {
    const params = new HttpParams()
      .set('plantationId', plantationId)
      .set('page', page)
      .set('taille', taille);
    return this.http.get<PageResponse<RecolteResponse>>(this.apiUrl + '/pagination', {
      params,
    });
  }

  consulterTotal(plantationId: number): Observable<TotalRecolteResponse> {
    const params = new HttpParams().set('plantationId', plantationId);
    return this.http.get<TotalRecolteResponse>(this.apiUrl + '/total', { params });
  }

  consulterParId(id: number): Observable<RecolteResponse> {
    return this.http.get<RecolteResponse>(this.apiUrl + '/' + id);
  }

  creer(request: RecolteRequest): Observable<RecolteResponse> {
    return this.http.post<RecolteResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: RecolteRequest): Observable<RecolteResponse> {
    return this.http.put<RecolteResponse>(this.apiUrl + '/' + id, request);
  }
}
