import { Pipe, PipeTransform } from '@angular/core';
import { StatutCampagne } from '../../core/models/campagne';

@Pipe({ name: 'statutCampagne' })
export class StatutCampagnePipe implements PipeTransform {
  transform(statut: StatutCampagne): string {
    if (statut === 'PLANIFIEE') return 'Planifiée';
    if (statut === 'EN_COURS') return 'En cours';
    return 'Clôturée';
  }
}
