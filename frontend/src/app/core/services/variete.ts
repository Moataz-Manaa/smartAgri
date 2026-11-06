import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { VarieteRequest, VarieteResponse } from '../models/variete';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class VarieteService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/varietes';

  consulterPage(
    page: number,
    taille: number,
    cultureId: number | null = null,
  ): Observable<PageResponse<VarieteResponse>> {
    let params = new HttpParams().set('page', page).set('taille', taille);
    if (cultureId !== null) params = params.set('cultureId', cultureId);
    return this.http.get<PageResponse<VarieteResponse>>(this.apiUrl + '/pagination', { params });
  }

  consulterToutes(cultureId: number | null = null): Observable<VarieteResponse[]> {
    let params = new HttpParams();
    if (cultureId !== null) params = params.set('cultureId', cultureId);
    return this.http.get<VarieteResponse[]>(this.apiUrl, { params });
  }

  consulterParId(id: number): Observable<VarieteResponse> {
    return this.http.get<VarieteResponse>(this.apiUrl + '/' + id);
  }

  creer(request: VarieteRequest): Observable<VarieteResponse> {
    return this.http.post<VarieteResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: VarieteRequest): Observable<VarieteResponse> {
    return this.http.put<VarieteResponse>(this.apiUrl + '/' + id, request);
  }
}
