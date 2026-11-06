import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CultureRequest, CultureResponse } from '../models/culture';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class CultureService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/cultures';

  consulterPage(page: number, taille: number): Observable<PageResponse<CultureResponse>> {
    const params = { page, taille };
    return this.http.get<PageResponse<CultureResponse>>(this.apiUrl + '/pagination', { params });
  }

  consulterToutes(): Observable<CultureResponse[]> {
    return this.http.get<CultureResponse[]>(this.apiUrl);
  }

  consulterParId(id: number): Observable<CultureResponse> {
    return this.http.get<CultureResponse>(this.apiUrl + '/' + id);
  }

  creer(request: CultureRequest): Observable<CultureResponse> {
    return this.http.post<CultureResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: CultureRequest): Observable<CultureResponse> {
    return this.http.put<CultureResponse>(this.apiUrl + '/' + id, request);
  }
}
