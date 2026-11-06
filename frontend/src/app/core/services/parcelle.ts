import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/page';

import { ParcelleRequest, ParcelleResponse } from '../models/parcelle';

@Injectable({
  providedIn: 'root',
})
export class ParcelleService {
  consulterPage(page: number, taille: number): Observable<PageResponse<ParcelleResponse>> {
    const params = { page, taille };
    return this.http.get<PageResponse<ParcelleResponse>>(this.apiUrl + '/pagination', { params });
  }

  private http = inject(HttpClient);

  private apiUrl = 'http://localhost:8081/api/parcelles';

  consulterToutes(): Observable<ParcelleResponse[]> {
    return this.http.get<ParcelleResponse[]>(this.apiUrl);
  }

  consulterParId(id: number): Observable<ParcelleResponse> {
    return this.http.get<ParcelleResponse>(this.apiUrl + '/' + id);
  }

  consulterParExploitation(exploitationId: number): Observable<ParcelleResponse[]> {
    return this.http.get<ParcelleResponse[]>(this.apiUrl + '/exploitation/' + exploitationId);
  }

  creer(request: ParcelleRequest): Observable<ParcelleResponse> {
    return this.http.post<ParcelleResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: ParcelleRequest): Observable<ParcelleResponse> {
    return this.http.put<ParcelleResponse>(this.apiUrl + '/' + id, request);
  }
}
