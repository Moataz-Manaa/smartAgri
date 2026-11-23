import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MeteoResponse } from '../models/meteo';

@Injectable({ providedIn: 'root' })
export class MeteoService {
  private http = inject(HttpClient);

  consulterParParcelle(parcelleId: number): Observable<MeteoResponse> {
    return this.http.get<MeteoResponse>(
      'http://localhost:8081/api/parcelles/' + parcelleId + '/meteo',
    );
  }
}
