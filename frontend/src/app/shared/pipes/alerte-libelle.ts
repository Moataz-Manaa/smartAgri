import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'alerteLibelle' })
export class AlerteLibellePipe implements PipeTransform {
  transform(valeur: string): string {
    const libelles: Record<string, string> = {
      CHALEUR: 'Chaleur',
      GEL: 'Gel',
      PLUIE_IMPORTANTE: 'Pluie importante',
      AVERTISSEMENT: 'Avertissement',
      CRITIQUE: 'Critique',
      ACTIVE: 'Active',
      LEVEE: 'Levée',
      EXPIREE: 'Expirée',
    };
    return libelles[valeur] ?? valeur;
  }
}
