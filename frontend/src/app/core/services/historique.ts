import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ActiviteResponse, TypeActivite } from '../models/historique';
import { PageResponse } from '../models/page';

@Injectable({ providedIn: 'root' })
export class HistoriqueService {
  private http = inject(HttpClient);

  consulterPage(
    plantationId: number,
    page: number,
    taille: number,
    type: TypeActivite | '',
    dateDebut: string,
    dateFin: string,
  ): Observable<PageResponse<ActiviteResponse>> {
    let params = new HttpParams()
      .set('plantationId', plantationId)
      .set('page', page)
      .set('taille', taille);
    if (type) params = params.set('type', type);
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin) params = params.set('dateFin', dateFin);
    return this.http.get<PageResponse<ActiviteResponse>>(
      'http://localhost:8081/api/historique/pagination',
      { params },
    );
  }
}
