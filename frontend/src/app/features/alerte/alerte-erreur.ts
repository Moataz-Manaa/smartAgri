import { HttpErrorResponse } from '@angular/common/http';

export function messageErreurAlerte(erreur: HttpErrorResponse): string {
  if (erreur.status === 401)
    return 'Votre session a expiré. Reconnectez-vous pour consulter les alertes.';
  if (erreur.status === 403) return 'Vos droits ne permettent plus de consulter ces alertes.';
  if (erreur.status === 404) return 'Cette alerte ou la parcelle sélectionnée est introuvable.';
  if (erreur.status === 400)
    return 'Les filtres sont invalides. Vérifiez les valeurs et les dates.';
  return 'Impossible de charger les alertes. Vérifiez votre connexion.';
}
