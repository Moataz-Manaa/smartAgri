import { Pipe, PipeTransform } from '@angular/core';
import { UniteQuantite } from '../../core/models/unite-quantite';

@Pipe({ name: 'uniteQuantite' })
export class UniteQuantitePipe implements PipeTransform {
  transform(unite: UniteQuantite): string {
    if (unite === 'GRAMME') return 'g';
    if (unite === 'KILOGRAMME') return 'kg';
    if (unite === 'MILLILITRE') return 'mL';
    if (unite === 'LITRE') return 'L';
    return '—';
  }
}
