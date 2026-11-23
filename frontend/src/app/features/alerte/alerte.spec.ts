import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AlerteResponse, AlerteFiltres, CompteurAlertesResponse } from '../../core/models/alerte';
import { PageResponse } from '../../core/models/page';
import { AlerteService } from '../../core/services/alerte';
import { ParcelleService } from '../../core/services/parcelle';
import { AuthService } from '../../core/services/auth';
import { AlerteListe } from './alerte-liste/alerte-liste';
import { AlerteDetail } from './alerte-detail/alerte-detail';
import { routes } from '../../app.routes';
import { App } from '../../app';

function exemple(): AlerteResponse {
  return {
    id: 1,
    parcelleId: 11,
    parcelleCode: 'PAR-11',
    parcelleNom: 'Oliviers',
    exploitationId: 7,
    exploitationNom: 'Ferme Sfax',
    type: 'CHALEUR',
    niveau: 'CRITIQUE',
    statut: 'ACTIVE',
    message: 'Température prévue élevée.',
    valeur: 40,
    seuil: 40,
    unite: '°C',
    latitude: 0,
    longitude: 0,
    source: 'OpenWeather',
    datePrevision: '2026-11-14T15:00:00Z',
    debutPeriode: '2026-11-14T15:00:00Z',
    dateDetection: '2026-11-14T12:00:00Z',
    derniereVerification: '2026-11-14T12:05:00Z',
    dateRecuperationMeteo: '2026-11-14T12:00:00Z',
    dateCloture: null,
    motifCloture: null,
    donneesRecentes: true,
    lue: false,
    dateLecture: null,
  };
}

describe('Parcours Angular des alertes', () => {
  let alerte: AlerteResponse;
  let service: {
    consulterPage: ReturnType<typeof vi.fn>;
    consulterParId: ReturnType<typeof vi.fn>;
    consulterCompteur: ReturnType<typeof vi.fn>;
    marquerLue: ReturnType<typeof vi.fn>;
  };
  let parcelles: { consulterToutes: ReturnType<typeof vi.fn> };
  let role: string;
  let connecte: boolean;
  let page: PageResponse<AlerteResponse>;

  beforeEach(() => {
    alerte = exemple();
    role = 'AGRICULTEUR';
    connecte = true;
    page = { contenu: [alerte], page: 0, taille: 10, totalElements: 1, totalPages: 1 };
    service = {
      consulterPage: vi.fn(() => of(page)),
      consulterParId: vi.fn(() => of(alerte)),
      consulterCompteur: vi.fn(() => of({ activesNonLues: 1, calculeLe: '2026-11-14T12:05:00Z' })),
      marquerLue: vi.fn(() => of({ ...alerte, lue: true, dateLecture: '2026-11-14T12:10:00Z' })),
    };
    parcelles = { consulterToutes: vi.fn(() => of([{ id: 11, code: 'PAR-11', nom: 'Oliviers' }])) };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: AlerteService, useValue: service },
        { provide: ParcelleService, useValue: parcelles },
        {
          provide: AuthService,
          useValue: {
            estConnecte: () => connecte,
            possedeRole: (r: string) => r === role,
            obtenirNomUtilisateur: () => 'Compte de test',
          },
        },
      ],
    });
  });

  async function ouvrirListe(url = '/alertes') {
    const navigation = await RouterTestingHarness.create();
    const composant = await navigation.navigateByUrl(url, AlerteListe);
    return { navigation, composant };
  }
  async function ouvrirDetail(url = '/alertes/1') {
    const navigation = await RouterTestingHarness.create();
    const composant = await navigation.navigateByUrl(url, AlerteDetail);
    return { navigation, composant };
  }
  function texte(navigation: RouterTestingHarness) {
    return navigation.routeNativeElement!.textContent!.replace(/\s+/g, ' ');
  }

  for (const r of ['ADMIN', 'AGRICULTEUR', 'AGRONOME']) {
    it('ouvre la liste, le détail et le lien de navigation pour ' + r, async () => {
      role = r;
      const { navigation } = await ouvrirListe();
      expect(texte(navigation)).toContain('Alertes agricoles');
      expect(texte(navigation)).toContain('PAR-11');
      await navigation.navigateByUrl('/alertes/1', AlerteDetail);
      expect(texte(navigation)).toContain('Marquer comme lue');
      const application = TestBed.createComponent(App);
      await application.whenStable();
      expect(application.nativeElement.querySelector('a[href="/alertes"]')).not.toBeNull();
    });
  }

  it('refuse les routes sans session et sans rôle autorisé', async () => {
    connecte = false;
    const navigation = await RouterTestingHarness.create();
    await navigation.navigateByUrl('/alertes');
    expect(TestBed.inject(Router).url).toBe('/acces-refuse');
    connecte = true;
    role = 'AUTRE';
    await navigation.navigateByUrl('/alertes/1');
    expect(TestBed.inject(Router).url).toBe('/acces-refuse');
    expect(service.consulterPage).not.toHaveBeenCalled();
    expect(service.consulterParId).not.toHaveBeenCalled();
  });

  it('charge les alertes actives par défaut et un compteur au périmètre explicite', async () => {
    const { navigation } = await ouvrirListe();
    expect(service.consulterPage).toHaveBeenCalledWith(
      0,
      10,
      expect.objectContaining({ statut: 'ACTIVE', lue: null }),
    );
    expect(service.consulterCompteur).toHaveBeenCalledWith(null);
    expect(texte(navigation)).toContain(
      'Toutes les parcelles accessibles, indépendamment des autres filtres',
    );
    expect(texte(navigation)).not.toContain('Actualiser');
  });

  it('applique les filtres au formulaire puis conserve leur copie pendant la pagination', async () => {
    const { navigation, composant } = await ouvrirListe();
    composant.filtres = {
      parcelleId: 11,
      type: 'GEL',
      niveau: 'CRITIQUE',
      statut: '',
      lue: false,
      dateDebut: '2026-11-14',
      dateFin: '2026-11-15',
    };
    navigation
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await navigation.fixture.whenStable();
    expect(service.consulterPage).toHaveBeenLastCalledWith(
      0,
      10,
      expect.objectContaining({ parcelleId: 11, type: 'GEL', lue: false }),
    );
    expect(service.consulterCompteur).toHaveBeenLastCalledWith(11);
    composant.filtres.type = 'CHALEUR';
    service.consulterPage.mockReturnValue(
      of({ ...page, page: 1, totalPages: 2, totalElements: 11 }),
    );
    composant.chargerPage(1);
    expect(service.consulterPage).toHaveBeenLastCalledWith(
      1,
      10,
      expect.objectContaining({ type: 'GEL' }),
    );
    composant.changerTaille(20);
    expect(service.consulterPage).toHaveBeenLastCalledWith(
      0,
      20,
      expect.objectContaining({ type: 'GEL' }),
    );
  });

  it('vérifie les dates et les filtres invalides avant une requête et réinitialise', async () => {
    const { composant } = await ouvrirListe();
    const appels = service.consulterPage.mock.calls.length;
    for (const dates of [
      ['2026-11-15', '2026-11-14'],
      ['2026-02-30', ''],
      ['0000-01-01', ''],
    ]) {
      composant.filtres.dateDebut = dates[0];
      composant.filtres.dateFin = dates[1];
      composant.appliquer();
      expect(composant.erreurFiltre()).not.toBe('');
      expect(composant.compteur()).toBeNull();
    }
    expect(service.consulterPage).toHaveBeenCalledTimes(appels);
    composant.reinitialiser();
    expect(composant.erreurFiltre()).toBe('');
    expect(service.consulterPage).toHaveBeenLastCalledWith(
      0,
      10,
      expect.objectContaining({ statut: 'ACTIVE', dateDebut: '' }),
    );
  });

  it('affiche les non-lues actives via le raccourci du compteur', async () => {
    const { composant } = await ouvrirListe();
    composant.afficherNonLues();
    expect(service.consulterPage).toHaveBeenLastCalledWith(
      0,
      10,
      expect.objectContaining({ statut: 'ACTIVE', lue: false }),
    );
  });

  it('conserve les filtres au retour du détail et relit le compteur après marquage', async () => {
    const { navigation, composant } = await ouvrirListe(
      '/alertes?parcelleId=11&lue=false&type=CHALEUR&taille=20',
    );
    const retour = composant.parametresRetour();
    const arbre = TestBed.inject(Router).createUrlTree(['/alertes/1'], { queryParams: retour });
    const detail = await navigation.navigateByUrl(
      TestBed.inject(Router).serializeUrl(arbre),
      AlerteDetail,
    );
    detail.marquerLue();
    service.consulterPage.mockReturnValue(
      of({ ...page, contenu: [], totalElements: 0, totalPages: 0 }),
    );
    service.consulterCompteur.mockReturnValue(
      of({ activesNonLues: 0, calculeLe: '2026-11-14T12:10:00Z' }),
    );
    const liste = await navigation.navigateByUrl(
      TestBed.inject(Router).serializeUrl(
        TestBed.inject(Router).createUrlTree(['/alertes'], { queryParams: detail.retour }),
      ),
      AlerteListe,
    );
    expect(liste.filtres).toEqual(
      expect.objectContaining({ parcelleId: 11, lue: false, type: 'CHALEUR' }),
    );
    expect(liste.taille()).toBe(20);
    expect(texte(navigation)).toContain('Aucune alerte pour cette sélection');
    expect(liste.compteur()?.activesNonLues).toBe(0);
  });

  it('prend en compte une autre parcelle dans une navigation sur la même liste', async () => {
    const { navigation } = await ouvrirListe('/alertes?parcelleId=11');
    await navigation.navigateByUrl('/alertes?parcelleId=12', AlerteListe);
    expect(service.consulterPage).toHaveBeenLastCalledWith(
      0,
      10,
      expect.objectContaining({ parcelleId: 12 }),
    );
    expect(service.consulterCompteur).toHaveBeenLastCalledWith(12);
  });

  it('annule un ancien chargement et n’affiche pas sa réponse tardive', async () => {
    const ancienne = new Subject<PageResponse<AlerteResponse>>();
    service.consulterPage.mockReturnValueOnce(ancienne);
    const { composant } = await ouvrirListe();
    expect(ancienne.observed).toBe(true);
    composant.filtres.type = 'GEL';
    composant.appliquer();
    expect(ancienne.observed).toBe(false);
    ancienne.next({ ...page, contenu: [{ ...alerte, parcelleNom: 'Anciennes données' }] });
    expect(composant.alertes()[0].parcelleNom).toBe('Oliviers');
  });

  it('laisse la liste consultable quand seul le compteur ou le filtre des parcelles échoue', async () => {
    service.consulterCompteur.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    parcelles.consulterToutes.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    const { navigation, composant } = await ouvrirListe();
    expect(composant.alertes()).toHaveLength(1);
    expect(composant.erreurCompteur()).not.toBe('');
    expect(composant.erreurParcelles()).not.toBe('');
    expect(texte(navigation)).toContain('PAR-11');
    expect(composant.compteur()).toBeNull();
  });

  it('efface les informations et annule les autres réponses après un refus de compteur', async () => {
    const compteur = new Subject<CompteurAlertesResponse>();
    service.consulterCompteur.mockReturnValue(compteur);
    const { composant } = await ouvrirListe();
    expect(composant.alertes()).toHaveLength(1);
    compteur.error(new HttpErrorResponse({ status: 403 }));
    expect(composant.alertes()).toHaveLength(0);
    expect(composant.compteur()).toBeNull();
    expect(composant.parcelles()).toHaveLength(0);
    expect(composant.erreur()).toContain('droits');
  });

  it('efface aussi le compteur après une perte d’accès à la liste', async () => {
    const { composant } = await ouvrirListe();
    service.consulterPage.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    composant.appliquer();
    expect(composant.compteur()).toBeNull();
    expect(composant.alertes()).toHaveLength(0);
    expect(composant.erreur()).toContain('droits');
  });

  it('revient à une page existante lorsque la dernière page est devenue vide', async () => {
    const { composant } = await ouvrirListe();
    service.consulterPage
      .mockReturnValueOnce(of({ ...page, contenu: [], page: 2, totalElements: 10, totalPages: 1 }))
      .mockReturnValue(of(page));
    composant.chargerPage(2);
    expect(service.consulterPage).toHaveBeenLastCalledWith(0, 10, expect.anything());
    expect(composant.alertes()).toHaveLength(1);
  });

  it('présente les états textuels, les valeurs zéro et les dates en UTC sans lecture automatique', async () => {
    alerte.valeur = 0;
    alerte.seuil = 0;
    alerte.type = 'GEL';
    alerte.statut = 'EXPIREE';
    alerte.donneesRecentes = false;
    alerte.motifCloture = 'Échéance passée';
    const { navigation } = await ouvrirDetail();
    expect(texte(navigation)).toContain('Gel');
    expect(texte(navigation)).toContain('Expirée');
    expect(texte(navigation)).toContain('0 °C');
    expect(texte(navigation)).toContain('14/11/2026 à 15:00');
    expect(texte(navigation)).toContain('Échéance (UTC)');
    expect(texte(navigation)).toContain('sont anciennes');
    expect(service.marquerLue).not.toHaveBeenCalled();
  });

  it('enregistre une lecture confirmée et empêche les doubles clics', async () => {
    const lecture = new Subject<AlerteResponse>();
    service.marquerLue.mockReturnValue(lecture);
    const { navigation, composant } = await ouvrirDetail();
    const bouton = navigation.routeNativeElement!.querySelector('button')!;
    bouton.click();
    composant.marquerLue();
    expect(service.marquerLue).toHaveBeenCalledTimes(1);
    expect(composant.enregistrement()).toBe(true);
    lecture.next({ ...alerte, lue: true, dateLecture: '2026-11-14T12:10:00Z' });
    navigation.detectChanges();
    expect(texte(navigation)).toContain('Lecture enregistrée pour votre compte');
    expect(texte(navigation)).toContain('Lue par vous le');
    expect(navigation.routeNativeElement!.querySelector('button')).toBeNull();
  });

  it('ne remplace pas la lecture par un succès fictif après une panne', async () => {
    service.marquerLue.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const { composant } = await ouvrirDetail();
    composant.marquerLue();
    expect(composant.alerte()?.lue).toBe(false);
    expect(composant.erreurLecture()).toContain('Impossible de confirmer');
    expect(composant.confirmation()).toBe('');
  });

  for (const statut of [401, 403, 404]) {
    it('retire le détail si le marquage reçoit ' + statut, async () => {
      service.marquerLue.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: statut })),
      );
      const { composant } = await ouvrirDetail();
      composant.marquerLue();
      expect(composant.alerte()).toBeNull();
      expect(composant.erreur()).not.toBe('');
    });
  }

  it('refuse un identifiant de détail invalide sans appel API', async () => {
    const { composant } = await ouvrirDetail('/alertes/invalide');
    expect(composant.erreur()).toContain('invalide');
    expect(service.consulterParId).not.toHaveBeenCalled();
  });

  it('annule la lecture et le détail précédents au changement d’identifiant', async () => {
    const lecture = new Subject<AlerteResponse>();
    service.marquerLue.mockReturnValue(lecture);
    const { navigation, composant } = await ouvrirDetail();
    composant.marquerLue();
    service.consulterParId.mockReturnValue(
      of({ ...alerte, id: 2, parcelleNom: 'Nouvelle parcelle' }),
    );
    await navigation.navigateByUrl('/alertes/2', AlerteDetail);
    expect(lecture.observed).toBe(false);
    expect(texte(navigation)).toContain('Nouvelle parcelle');
    expect(composant.enregistrement()).toBe(false);
  });

  it('annule toutes les requêtes à la destruction de la liste', async () => {
    const liste = new Subject<PageResponse<AlerteResponse>>();
    const compteur = new Subject<CompteurAlertesResponse>();
    service.consulterPage.mockReturnValue(liste);
    service.consulterCompteur.mockReturnValue(compteur);
    const { navigation } = await ouvrirListe();
    await navigation.navigateByUrl('/acces-refuse');
    expect(liste.observed).toBe(false);
    expect(compteur.observed).toBe(false);
  });
});

describe('HTTP des alertes', () => {
  it('transmet les filtres, y compris false, uniquement à SmartAgri', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(AlerteService);
    const http = TestBed.inject(HttpTestingController);
    const filtres: AlerteFiltres = {
      parcelleId: 11,
      type: 'GEL',
      niveau: 'CRITIQUE',
      statut: 'ACTIVE',
      lue: false,
      dateDebut: '2026-11-14',
      dateFin: '2026-11-15',
    };
    service.consulterPage(1, 20, filtres).subscribe();
    const demande = http.expectOne((r) => r.url === 'http://localhost:8081/api/alertes/pagination');
    expect(demande.request.params.get('lue')).toBe('false');
    expect(demande.request.params.get('parcelleId')).toBe('11');
    expect(demande.request.params.get('dateFin')).toBe('2026-11-15');
    expect(demande.request.params.get('page')).toBe('1');
    demande.flush({ contenu: [], page: 1, taille: 20, totalElements: 0, totalPages: 0 });
    service.consulterCompteur(null).subscribe();
    const compteur = http.expectOne('http://localhost:8081/api/alertes/compteur');
    expect(compteur.request.params.has('parcelleId')).toBe(false);
    compteur.flush({ activesNonLues: 0, calculeLe: '' });
    service.marquerLue(1).subscribe();
    const lecture = http.expectOne('http://localhost:8081/api/alertes/1/lecture');
    expect(lecture.request.method).toBe('PUT');
    expect(lecture.request.body).toEqual({});
    lecture.flush(exemple());
    http.verify();
  });
});
