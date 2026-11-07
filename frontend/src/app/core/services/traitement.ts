import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TraitementRequest, TraitementResponse } from '../models/traitement';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class TraitementService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/traitements';

  consulterPage(
    plantationId: number,
    page: number,
    taille: number,
  ): Observable<PageResponse<TraitementResponse>> {
    const params = new HttpParams()
      .set('plantationId', plantationId)
      .set('page', page)
      .set('taille', taille);
    return this.http.get<PageResponse<TraitementResponse>>(this.apiUrl + '/pagination', {
      params,
    });
  }

  consulterParId(id: number): Observable<TraitementResponse> {
    return this.http.get<TraitementResponse>(this.apiUrl + '/' + id);
  }

  creer(request: TraitementRequest): Observable<TraitementResponse> {
    return this.http.post<TraitementResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: TraitementRequest): Observable<TraitementResponse> {
    return this.http.put<TraitementResponse>(this.apiUrl + '/' + id, request);
  }
}
