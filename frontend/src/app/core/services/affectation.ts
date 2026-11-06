import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/page';
import { AffectationRequest, AffectationResponse } from '../models/affectation';

@Injectable({ providedIn: 'root' })
export class AffectationService {
  consulterPage(
    page: number,
    taille: number,
    parcelleId: number | null = null,
  ): Observable<PageResponse<AffectationResponse>> {
    let params = new HttpParams().set('page', page).set('taille', taille);
    if (parcelleId !== null) {
      params = params.set('parcelleId', parcelleId);
    }
    return this.http.get<PageResponse<AffectationResponse>>(this.apiUrl + '/pagination', {
      params,
    });
  }

  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/affectations';

  consulterToutes(): Observable<AffectationResponse[]> {
    return this.http.get<AffectationResponse[]>(this.apiUrl);
  }

  creer(request: AffectationRequest): Observable<AffectationResponse> {
    return this.http.post<AffectationResponse>(this.apiUrl, request);
  }

  retirer(id: number): Observable<void> {
    return this.http.delete<void>(this.apiUrl + '/' + id);
  }
}
