import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  IrrigationRequest,
  IrrigationResponse,
  TotalIrrigationResponse,
} from '../models/irrigation';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class IrrigationService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/irrigations';

  consulterPage(
    plantationId: number,
    page: number,
    taille: number,
  ): Observable<PageResponse<IrrigationResponse>> {
    const params = new HttpParams()
      .set('plantationId', plantationId)
      .set('page', page)
      .set('taille', taille);
    return this.http.get<PageResponse<IrrigationResponse>>(this.apiUrl + '/pagination', {
      params,
    });
  }

  consulterTotal(plantationId: number): Observable<TotalIrrigationResponse> {
    const params = new HttpParams().set('plantationId', plantationId);
    return this.http.get<TotalIrrigationResponse>(this.apiUrl + '/total', { params });
  }

  consulterParId(id: number): Observable<IrrigationResponse> {
    return this.http.get<IrrigationResponse>(this.apiUrl + '/' + id);
  }

  creer(request: IrrigationRequest): Observable<IrrigationResponse> {
    return this.http.post<IrrigationResponse>(this.apiUrl, request);
  }

  modifier(id: number, request: IrrigationRequest): Observable<IrrigationResponse> {
    return this.http.put<IrrigationResponse>(this.apiUrl + '/' + id, request);
  }
}
