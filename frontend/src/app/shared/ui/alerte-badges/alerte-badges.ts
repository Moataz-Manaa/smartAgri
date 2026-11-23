import { Component, Input } from '@angular/core';
import { AlerteResponse } from '../../../core/models/alerte';
import { AlerteLibellePipe } from '../../pipes/alerte-libelle';

@Component({
  selector: 'app-alerte-badges',
  imports: [AlerteLibellePipe],
  templateUrl: './alerte-badges.html',
  host: { class: 'block' },
})
export class AlerteBadges {
  @Input() alerte: AlerteResponse | null = null;
}
