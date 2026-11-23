/** Recette Firefox avec API et authentification fictives, sans accès aux services habituels.
 * Depuis frontend : node tools/recette-sprint3.cjs
 * PostgreSQL/Keycloak sont testés séparément par keycloak/verify_backend.py.
 */
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const net = require('node:net');

const racine = path.resolve(__dirname, '../..');
const rapports = path.join(racine, 'backend/target/recette-sprint3-ui');
const firefoxPath =
  process.env.SMARTAGRI_FIREFOX_PATH || 'C:/Program Files/Mozilla Firefox/firefox.exe';
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function portLibre() {
  const serveur = net.createServer();
  await new Promise((resolve) => serveur.listen(0, '127.0.0.1', resolve));
  const port = serveur.address().port;
  await new Promise((resolve) => serveur.close(resolve));
  return port;
}

async function recette() {
  if (!fs.existsSync(firefoxPath))
    throw new Error('Firefox introuvable. Définissez SMARTAGRI_FIREFOX_PATH.');
  fs.mkdirSync(rapports, { recursive: true });
  const journal = fs.openSync(path.join(rapports, 'angular.log'), 'w');
  const portAngular = await portLibre();
  const angular = spawn(
    process.execPath,
    [
      'node_modules/@angular/cli/bin/ng.js',
      'serve',
      '--host',
      '127.0.0.1',
      '--port',
      String(portAngular),
    ],
    { cwd: path.join(racine, 'frontend'), windowsHide: true, stdio: ['ignore', journal, journal] },
  );
  let role = 'AGRICULTEUR';
  let etatMeteo = 'DISPONIBLE';
  let refus = false;
  let firefox;
  let socket;
  const lectures = new Set();
  const appels = [];
  const source = {
    parcelleId: 11,
    exploitationId: 7,
    exploitationNom: 'Ferme de recette',
    latitude: 34.74,
    longitude: 10.76,
    source: 'OpenWeather',
    lienSource: 'https://openweathermap.org/',
    message: 'Données fictives de recette.',
    donnees: {
      recupereesLe: '2026-11-14T08:00:00Z',
      decalageUtcSecondes: 3600,
      conditions: {
        dateDonnees: '2026-11-14T08:00:00Z',
        temperatureCelsius: 40,
        humiditePourcent: 65,
        pluieDerniereHeureMillimetres: 0,
        description: 'Ciel clair',
      },
      previsions: Array.from({ length: 40 }, (_, i) => ({
        datePrevision: new Date(Date.UTC(2026, 10, 14, 9 + i * 3)).toISOString(),
        temperatureCelsius: 40,
        humiditePourcent: 65,
        probabilitePrecipitationsPourcent: 80,
        pluieTroisHeuresMillimetres: 40,
        description: 'Prévision fictive de recette',
      })),
    },
  };
  const alertes = [
    { id: 1, type: 'CHALEUR', valeur: 40, seuil: 40, unite: '°C' },
    { id: 2, type: 'PLUIE_IMPORTANTE', valeur: 40, seuil: 40, unite: 'mm/3 h' },
  ].map((a) => ({
    ...a,
    parcelleId: 11,
    parcelleCode: 'PAR-11',
    parcelleNom: 'Oliviers',
    exploitationId: 7,
    exploitationNom: 'Ferme de recette',
    niveau: 'CRITIQUE',
    statut: 'ACTIVE',
    message: 'Le seuil de suivi est atteint dans cette prévision fictive.',
    latitude: 34.74,
    longitude: 10.76,
    source: 'OpenWeather',
    datePrevision: '2026-11-14T15:00:00Z',
    debutPeriode: '2026-11-14T12:00:00Z',
    dateDetection: '2026-11-14T08:00:00Z',
    derniereVerification: '2026-11-14T08:00:00Z',
    dateRecuperationMeteo: '2026-11-14T08:00:00Z',
    dateCloture: null,
    motifCloture: null,
    donneesRecentes: true,
  }));
  const avecLecture = (a) => ({
    ...a,
    lue: lectures.has(role + ':' + a.id),
    dateLecture: lectures.has(role + ':' + a.id) ? '2026-11-14T10:00:00Z' : null,
  });
  const activites = Array.from({ length: 5 }, (_, i) => ({
    id: i + 1,
    type: ['RECOLTE', 'IRRIGATION', 'TRAITEMENT', 'FERTILISATION', 'OBSERVATION'][i],
    date: '2026-11-14',
    plantationId: 9,
    parcelleId: 11,
    parcelleCode: 'PAR-11',
    parcelleNom: 'Oliviers',
    campagneNom: 'Campagne 2026',
    description: 'Activité fictive de recette.',
  }));
  const proxy = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      if (url.pathname.startsWith('/api/')) {
        appels.push({ role, methode: req.method, url: req.url });
        if (refus && role !== 'ADMIN') {
          res.writeHead(403, { 'Content-Type': 'application/json' });
          res.end('{"message":"Accès retiré pour la recette"}');
          return;
        }
        let body;
        if (url.pathname === '/api/parcelles/11/meteo')
          body = {
            ...source,
            statut: etatMeteo,
            donnees: etatMeteo === 'INDISPONIBLE' ? null : source.donnees,
          };
        else if (url.pathname === '/api/parcelles/11')
          body = {
            id: 11,
            code: 'PAR-11',
            nom: 'Oliviers',
            superficie: 5,
            exploitationId: 7,
            exploitationNom: 'Ferme de recette',
            exploitationCode: 'EXP-07',
            typeSol: 'Argileux',
            typeIrrigation: 'Goutte à goutte',
          };
        else if (url.pathname === '/api/parcelles')
          body = [{ id: 11, code: 'PAR-11', nom: 'Oliviers' }];
        else if (url.pathname === '/api/campagnes')
          body = [{ id: 3, nom: 'Campagne 2026', parcelleIds: [11], parcelles: [] }];
        else if (url.pathname === '/api/alertes/compteur')
          body = {
            activesNonLues: alertes.map(avecLecture).filter((a) => !a.lue).length,
            calculeLe: '2026-11-14T10:00:00Z',
          };
        else if (url.pathname === '/api/alertes/pagination') {
          const contenu = alertes
            .map(avecLecture)
            .filter((a) =>
              ['parcelleId', 'type', 'niveau', 'statut', 'lue'].every(
                (k) => !url.searchParams.has(k) || url.searchParams.get(k) === String(a[k]),
              ),
            );
          body = {
            contenu,
            page: 0,
            taille: 10,
            totalElements: contenu.length,
            totalPages: contenu.length ? 1 : 0,
          };
        } else if (/^\/api\/alertes\/\d+(\/lecture)?$/.test(url.pathname)) {
          const id = Number(url.pathname.split('/')[3]);
          if (req.method === 'PUT') lectures.add(role + ':' + id);
          body = avecLecture(alertes.find((a) => a.id === id));
        } else if (url.pathname === '/api/tableau-de-bord/synthese')
          body = {
            dateDebut: url.searchParams.get('dateDebut'),
            dateFin: url.searchParams.get('dateFin'),
            nombreParcelles: 1,
            nombreCampagnes: 1,
            nombrePlantations: 1,
            nombreActivites: 5,
            volumeIrrigationMetresCubes: 2.5,
            quantiteRecolteeKilogrammes: 1100,
          };
        else if (url.pathname === '/api/tableau-de-bord/activites') body = activites;
        else if (url.pathname === '/api/tableau-de-bord/alertes')
          body = {
            actives: 2,
            activesNonLues: alertes.map(avecLecture).filter((a) => !a.lue).length,
            calculeLe: '2026-11-14T10:00:00Z',
          };
        else if (url.pathname.endsWith('/pagination'))
          body = { contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 };
        else {
          res.writeHead(404);
          res.end('{}');
          return;
        }
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(body));
        return;
      }
      const amont = await fetch(`http://127.0.0.1:${portAngular}${req.url}`);
      const type = amont.headers.get('content-type') || '';
      let body = Buffer.from(await amont.arrayBuffer());
      if (type.includes('javascript')) {
        let code = body
          .toString()
          .replaceAll('http://localhost:8081', `http://127.0.0.1:${proxy.address().port}`);
        // Transformation limitée aux réponses de ce serveur de recette, jamais aux fichiers de l'application.
        if (code.includes('async initialiser()')) {
          const initialisation = /async initialiser\(\) \{[\s\S]*?(?=  async connecter\(\))/;
          if (!initialisation.test(code)) throw new Error('Initialisation de test non reconnue.');
          code = code.replace(
            initialisation,
            `async initialiser() { this.keycloak={authenticated:true,hasRealmRole:role=>role===${JSON.stringify(role)},tokenParsed:{preferred_username:${JSON.stringify('Test ' + role)}},token:'fixture-only',updateToken:async()=>false}; }\n`,
          );
        }
        code = code.replace(
          /import\.meta\.hot = __vite__createHotContext\([^;]+;/g,
          'import.meta.hot = undefined;',
        );
        body = Buffer.from(code);
      }
      res.writeHead(amont.status, { 'Content-Type': type });
      res.end(body);
    } catch (erreur) {
      res.writeHead(500);
      res.end(erreur.message);
    }
  });
  const resultats = [];
  try {
    let pret = false;
    for (let i = 0; i < 180; i++) {
      try {
        pret = (await fetch(`http://127.0.0.1:${portAngular}`)).ok;
        if (pret) break;
      } catch {}
      await pause(500);
    }
    if (!pret) throw new Error('Angular ne démarre pas. Voir angular.log.');
    await new Promise((resolve) => proxy.listen(0, '127.0.0.1', resolve));
    const origine = `http://127.0.0.1:${proxy.address().port}`;
    const port = await portLibre();
    const profil = fs.mkdtempSync(path.join(rapports, 'firefox-profil-'));
    firefox = spawn(
      firefoxPath,
      ['--headless', '--no-remote', '--profile', profil, '--remote-debugging-port', String(port)],
      { windowsHide: true, stdio: 'ignore' },
    );
    for (let i = 0; i < 80; i++) {
      try {
        socket = await new Promise((resolve, reject) => {
          const ws = new WebSocket(`ws://127.0.0.1:${port}/session`);
          ws.onopen = () => resolve(ws);
          ws.onerror = () => reject(new Error('Démarrage Firefox'));
        });
        break;
      } catch {
        await pause(250);
      }
    }
    if (!socket) throw new Error('Firefox ne démarre pas.');
    let sequence = 0;
    const attentes = new Map();
    socket.onmessage = ({ data }) => {
      const message = JSON.parse(data);
      const attente = attentes.get(message.id);
      if (attente) {
        attentes.delete(message.id);
        message.type === 'error'
          ? attente.reject(new Error(JSON.stringify(message)))
          : attente.resolve(message.result);
      }
    };
    const envoyer = (method, params) =>
      new Promise((resolve, reject) => {
        const id = ++sequence;
        attentes.set(id, { resolve, reject });
        socket.send(JSON.stringify({ id, method, params }));
      });
    await envoyer('session.new', { capabilities: {} });
    const { context } = await envoyer('browsingContext.create', { type: 'tab' });
    const evaluer = async (expression) => {
      const reponse = await envoyer('script.evaluate', {
        target: { context },
        awaitPromise: true,
        expression,
      });
      if (reponse.type === 'exception') throw new Error(JSON.stringify(reponse));
      return reponse.result.value;
    };
    const attendre = async (condition) => {
      const reponse = await evaluer(
        `new Promise(resolve=>{let i=0;const verifier=()=>{if(${condition})resolve('pret');else if(++i>100)resolve(document.body.innerText);else setTimeout(verifier,100);};verifier();})`,
      );
      if (reponse !== 'pret') throw new Error('Écran inattendu : ' + reponse);
    };
    const ouvrir = (url) =>
      envoyer('browsingContext.navigate', { context, url: origine + url, wait: 'complete' });
    const cliquer = async (selecteur) => {
      const visible = await evaluer(
        `(()=>{const e=document.querySelector(${JSON.stringify(selecteur)});if(!e)return false;e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();if(r.width===0||r.height===0)return false;e.click();return true;})()`,
      );
      if (!visible) throw new Error('Action invisible : ' + selecteur);
    };
    const verifierVue = async (ecran, selecteurs, largeur) => {
      const mesures = JSON.parse(
        await evaluer(
          `JSON.stringify((()=>{const checks=${JSON.stringify(selecteurs)}.map(s=>{const e=document.querySelector(s);if(!e)return {s,visible:false};e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect(),hit=document.elementFromPoint(r.left+Math.min(20,r.width/2),Math.max(1,r.top+Math.min(20,r.height/2)));return {s,visible:!!hit&&(e===hit||e.contains(hit))};});return {debordement:document.documentElement.scrollWidth>innerWidth,checks};})())`,
        ),
      );
      if (mesures.debordement || mesures.checks.some((c) => !c.visible))
        throw new Error(ecran + ' : ' + JSON.stringify(mesures));
      const resultat = { role, largeur, ecran, ...mesures };
      resultats.push(resultat);
      console.log(JSON.stringify(resultat));
      if (role === 'AGRICULTEUR' && [390, 1920].includes(largeur)) {
        await evaluer(`document.querySelector(${JSON.stringify(selecteurs[0])}).scrollIntoView()`);
        const capture = await envoyer('browsingContext.captureScreenshot', { context });
        fs.writeFileSync(
          path.join(rapports, `${ecran}-${largeur}.png`),
          Buffer.from(capture.data, 'base64'),
        );
      }
    };
    for (role of ['ADMIN', 'AGRICULTEUR', 'AGRONOME']) {
      for (const largeur of [320, 390, 768, 1024, 1280, 1920]) {
        lectures.delete(role + ':1');
        await envoyer('browsingContext.setViewport', {
          context,
          viewport: { width: largeur, height: 900 },
        });
        await ouvrir('/parcelles/11');
        await attendre(`document.querySelectorAll('app-meteo-parcelle ol li').length===10`);
        await attendre(
          `document.querySelector('app-header')?.textContent.includes(${JSON.stringify('Test ' + role)})`,
        );
        await verifierVue(
          'meteo',
          [
            'app-meteo-parcelle .grid',
            'app-meteo-parcelle ol li:last-child',
            'app-meteo-parcelle footer',
            'app-plantation-liste h2',
          ],
          largeur,
        );
        await cliquer('app-parcelle-detail a[href^="/alertes"]');
        await attendre(`document.querySelectorAll('app-alerte-liste li').length===2`);
        await verifierVue(
          'alertes',
          ['app-alerte-liste form', 'app-alerte-liste li:last-child'],
          largeur,
        );
        if ((await evaluer(`document.querySelector('#alerte-parcelle').selectedIndex`)) !== 1)
          throw new Error('Parcelle du lien non sélectionnée.');
        const avantLecture = appels.filter((a) => a.methode === 'PUT').length;
        await cliquer('app-alerte-liste li a');
        await attendre(
          `document.querySelector('app-alerte-detail button')?.textContent.includes('Marquer comme lue')`,
        );
        if (appels.filter((a) => a.methode === 'PUT').length !== avantLecture)
          throw new Error('Lecture automatique indue.');
        await verifierVue('detail', ['app-alerte-detail button', 'app-alerte-detail dl'], largeur);
        await cliquer('app-alerte-detail button');
        await attendre(
          `document.querySelector('app-alerte-detail')?.textContent.includes('Lue par vous le')`,
        );
        await cliquer('app-alerte-detail a');
        await attendre(
          `document.querySelector('app-alerte-liste li')?.textContent.includes('Lue par vous')`,
        );
        if (largeur < 1024) {
          await cliquer('app-sidebar button');
          await attendre(
            `document.querySelector('app-sidebar button').getAttribute('aria-expanded')==='true'`,
          );
        }
        await cliquer('app-sidebar a[href="/accueil"]');
        await attendre(
          `document.querySelector('[data-testid="synthese-bilan"]') && document.querySelectorAll('app-tableau-de-bord li').length===5 && document.querySelector('[data-testid="alertes-bilan"]')`,
        );
        if (
          largeur < 1024 &&
          (await evaluer(
            `document.querySelector('app-sidebar button').getAttribute('aria-expanded')`,
          )) !== 'false'
        )
          throw new Error('Menu mobile resté ouvert après navigation.');
        if (
          await evaluer(
            `[...document.querySelectorAll('button')].some(e=>e.textContent.trim()==='Actualiser')`,
          )
        )
          throw new Error('Bouton Actualiser inattendu.');
        if (
          !(await evaluer(
            `document.querySelector('app-tableau-de-bord').textContent.includes('Dont 1 non lues par vous')`,
          ))
        )
          throw new Error('Compteur personnel non mis à jour.');
        await verifierVue(
          'bilan',
          [
            '[data-testid="volume-bilan"]',
            '[data-testid="recolte-bilan"]',
            'app-tableau-de-bord li:last-child',
            '[data-testid="alertes-bilan"]',
          ],
          largeur,
        );
      }
    }
    role = 'AGRICULTEUR';
    etatMeteo = 'PERIMEE';
    await ouvrir('/parcelles/11');
    await attendre(
      `document.querySelector('app-meteo-parcelle')?.textContent.includes('Dernières données connues')`,
    );
    if (!(await evaluer(`!!document.querySelector('app-meteo-parcelle .grid')`)))
      throw new Error('Données périmées masquées.');
    etatMeteo = 'INDISPONIBLE';
    await ouvrir('/parcelles/11');
    await attendre(
      `document.querySelector('app-meteo-parcelle')?.textContent.includes('Météo indisponible')`,
    );
    if (await evaluer(`!!document.querySelector('app-meteo-parcelle .grid')`))
      throw new Error('Fausses mesures pendant une panne.');
    refus = true;
    await ouvrir('/alertes/1');
    await attendre(`!!document.querySelector('app-alerte-detail [role="alert"]')`);
    if (await evaluer(`!!document.querySelector('app-alerte-detail dl')`))
      throw new Error('Ancien détail après refus.');
    await ouvrir('/accueil');
    await attendre(`!!document.querySelector('app-tableau-de-bord [role="alert"]')`);
    if (await evaluer(`!!document.querySelector('[data-testid="synthese-bilan"]')`))
      throw new Error('Ancien bilan après refus.');
    fs.writeFileSync(
      path.join(rapports, 'resultats.json'),
      JSON.stringify(
        {
          succes: true,
          api: 'fictive',
          vues: resultats,
          parcours: 'météo → alertes → détail → lecture → accueil',
          controles: ['périmées', 'indisponibles', 'refus détail', 'refus bilan'],
        },
        null,
        2,
      ),
    );
    console.log(
      'RECETTE UI RÉUSSIE : 3 rôles × 6 largeurs × 4 écrans, parcours et états dégradés. API fictive.',
    );
    await envoyer('session.end', {});
  } finally {
    socket?.close();
    firefox?.kill();
    proxy.close();
    angular.kill();
    fs.closeSync(journal);
  }
}
recette().catch((erreur) => {
  console.error(erreur.message);
  process.exitCode = 1;
});
