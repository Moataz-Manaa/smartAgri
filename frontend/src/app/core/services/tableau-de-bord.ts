import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ActiviteRecenteResponse,
  AlertesTableauDeBordResponse,
  SyntheseResponse,
  TableauDeBordFiltres,
} from '../models/tableau-de-bord';

@Injectable({ providedIn: 'root' })
export class TableauDeBordService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8081/api/tableau-de-bord';

  consulterSynthese(filtres: TableauDeBordFiltres): Observable<SyntheseResponse> {
    return this.http.get<SyntheseResponse>(this.apiUrl + '/synthese', {
      params: this.parametres(filtres),
    });
  }

  consulterActivites(filtres: TableauDeBordFiltres): Observable<ActiviteRecenteResponse[]> {
    return this.http.get<ActiviteRecenteResponse[]>(this.apiUrl + '/activites', {
      params: this.parametres(filtres),
    });
  }

  consulterAlertes(filtres: TableauDeBordFiltres): Observable<AlertesTableauDeBordResponse> {
    return this.http.get<AlertesTableauDeBordResponse>(this.apiUrl + '/alertes', {
      params: this.parametres(filtres),
    });
  }

  private parametres(filtres: TableauDeBordFiltres): HttpParams {
    let params = new HttpParams()
      .set('dateDebut', filtres.dateDebut)
      .set('dateFin', filtres.dateFin);
    if (filtres.parcelleId !== null) params = params.set('parcelleId', filtres.parcelleId);
    if (filtres.campagneId !== null) params = params.set('campagneId', filtres.campagneId);
    return params;
  }
}
