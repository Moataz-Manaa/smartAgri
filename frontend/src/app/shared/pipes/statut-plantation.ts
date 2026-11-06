import { Pipe, PipeTransform } from '@angular/core';
import { StatutPlantation } from '../../core/models/plantation';

@Pipe({ name: 'statutPlantation' })
export class StatutPlantationPipe implements PipeTransform {
  transform(statut: StatutPlantation): string {
    if (statut === 'PLANIFIEE') return 'Planifiée';
    if (statut === 'TERMINEE') return 'Terminée';
    return 'En cours';
  }
}
