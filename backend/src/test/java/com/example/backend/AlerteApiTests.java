package com.example.backend;

import com.example.backend.dto.meteo.*;
import com.example.backend.entity.*;
import com.example.backend.enums.*;
import com.example.backend.repository.*;
import com.example.backend.service.AlerteCalculService;
import com.example.backend.service.CompteKeycloakService;
import com.example.backend.service.TokenService;
import com.example.backend.support.HorlogeMeteoTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:smartagri-api-alertes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AlerteApiTests {
    @Autowired MockMvc api;
    @Autowired AlerteRepository alertes;
    @Autowired LectureAlerteRepository lectures;
    @Autowired ParcelleRepository parcelles;
    @Autowired ExploitationRepository exploitations;
    @Autowired UtilisateurRepository utilisateurs;
    @Autowired AffectationRepository affectations;
    @Autowired AlerteCalculService calcul;
    @MockitoBean Clock meteoHorloge;
    @MockitoBean TokenService tokens;
    @MockitoBean CompteKeycloakService comptes;
    HorlogeMeteoTest horloge;
    ObjectMapper json = new ObjectMapper();
    Parcelle p1, p2, p3;
    Alerte chaleur, pluie, gel, horsAcces, passee, levee, expiree;
    Affectation affectationA1;

    @BeforeEach void preparer() throws Exception {
        lectures.deleteAll();
        alertes.deleteAll();
        affectations.deleteAll();
        parcelles.deleteAll();
        exploitations.deleteAll();
        utilisateurs.deleteAll();
        horloge = new HorlogeMeteoTest();
        when(meteoHorloge.instant()).thenAnswer(invocation -> horloge.instant());
        p1 = parcelle("1");
        p2 = parcelle("2");
        p3 = parcelle("3");
        identite("ADMIN", Role.ADMIN);
        Utilisateur a = identite("A", Role.AGRICULTEUR);
        Utilisateur b = identite("B", Role.AGRICULTEUR);
        Utilisateur agronome = identite("AGRONOME", Role.AGRONOME);
        identite("SANS", Role.AGRICULTEUR);
        affectationA1 = affecter(a, p1);
        affecter(a, p2);
        affecter(b, p1);
        affecter(b, p3);
        affecter(agronome, p1);
        chaleur = alerte(p1, TypeAlerte.CHALEUR, NiveauAlerte.AVERTISSEMENT, StatutAlerte.ACTIVE, horloge.instant().plusSeconds(3600));
        pluie = alerte(p1, TypeAlerte.PLUIE_IMPORTANTE, NiveauAlerte.CRITIQUE, StatutAlerte.ACTIVE, horloge.instant().plusSeconds(3600));
        gel = alerte(p2, TypeAlerte.GEL, NiveauAlerte.CRITIQUE, StatutAlerte.ACTIVE, horloge.instant().plusSeconds(3600));
        horsAcces = alerte(p3, TypeAlerte.CHALEUR, NiveauAlerte.CRITIQUE, StatutAlerte.ACTIVE, horloge.instant().plusSeconds(3600));
        passee = alerte(p1, TypeAlerte.CHALEUR, NiveauAlerte.CRITIQUE, StatutAlerte.ACTIVE, horloge.instant().minusSeconds(1));
        levee = alerte(p1, TypeAlerte.GEL, NiveauAlerte.AVERTISSEMENT, StatutAlerte.LEVEE, horloge.instant().plusSeconds(7200));
        expiree = alerte(p1, TypeAlerte.CHALEUR, NiveauAlerte.CRITIQUE, StatutAlerte.EXPIREE, horloge.instant().plusSeconds(14400));
    }

    Parcelle parcelle(String suffixe) {
        Exploitation exploitation = exploitations.saveAndFlush(Exploitation.builder().code("EXP-" + suffixe)
                .nom("Ferme " + suffixe).latitude(34.74).longitude(10.76).build());
        return parcelles.saveAndFlush(Parcelle.builder().code("PAR-" + suffixe).nom("Parcelle " + suffixe)
                .superficie(5.0).exploitation(exploitation).build());
    }

    Utilisateur identite(String nom, Role role) throws Exception {
        String keycloakId = "compte-" + nom;
        when(tokens.verifier(nom)).thenReturn(new JWTClaimsSet.Builder().subject(keycloakId).build());
        UserRepresentation compte = new UserRepresentation();
        compte.setEnabled(true);
        when(comptes.consulter(keycloakId)).thenReturn(compte);
        when(comptes.consulterRole(keycloakId)).thenReturn(role);
        if (role == Role.ADMIN) return null;
        return utilisateurs.saveAndFlush(Utilisateur.builder().keycloakId(keycloakId).nom(nom).prenom("Test")
                .email(nom.toLowerCase() + "@example.test").build());
    }

    Affectation affecter(Utilisateur utilisateur, Parcelle parcelle) {
        return affectations.saveAndFlush(Affectation.builder().utilisateur(utilisateur).parcelle(parcelle).build());
    }

    Alerte alerte(Parcelle parcelle, TypeAlerte type, NiveauAlerte niveau, StatutAlerte statut, Instant echeance) {
        return alertes.saveAndFlush(Alerte.builder().parcelle(parcelle).type(type).niveau(niveau).statut(statut)
                .message("Règle de suivi météo").valeur(40.0).seuil(35.0).unite(type == TypeAlerte.PLUIE_IMPORTANTE ? "mm/3 h" : "°C")
                .latitude(34.74).longitude(10.76).datePrevision(echeance)
                .debutPeriode(type == TypeAlerte.PLUIE_IMPORTANTE ? echeance.minusSeconds(10800) : echeance)
                .dateDetection(horloge.instant()).derniereVerification(horloge.instant())
                .dateRecuperationMeteo(horloge.instant()).build());
    }

    JsonNode detail(Long id, String compte) throws Exception {
        return json.readTree(api.perform(get("/api/alertes/" + id).header("Authorization", "Bearer " + compte))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    void compteur(String compte, int attendu) throws Exception {
        api.perform(get("/api/alertes/compteur").header("Authorization", "Bearer " + compte))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activesNonLues").value(attendu))
                .andExpect(jsonPath("$.calculeLe").exists());
    }

    @Test void limiterLesListesEtLesCompteursAuxAffectationsActuellesSansDoublons() throws Exception {
        for (String compte : List.of("A", "B")) {
            api.perform(get("/api/alertes/pagination").header("Authorization", "Bearer " + compte))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6));
            compteur(compte, 3);
        }
        api.perform(get("/api/alertes/pagination").header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
        compteur("AGRONOME", 2);
        api.perform(get("/api/alertes/pagination").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(7));
        compteur("ADMIN", 4);
    }

    @Test void renvoyerUneListeVideEtUnCompteurNulSansAffectation() throws Exception {
        api.perform(get("/api/alertes/pagination").header("Authorization", "Bearer SANS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.contenu", hasSize(0)));
        compteur("SANS", 0);
    }

    @Test void exposerLesUnitesEtLesDatesSourcesSansLesIdentitesDeLecture() throws Exception {
        JsonNode resultat = detail(pluie.getId(), "A");
        assertThat(resultat.get("parcelleCode").asText()).isEqualTo("PAR-1");
        assertThat(resultat.get("source").asText()).isEqualTo("OpenWeather");
        assertThat(resultat.get("unite").asText()).isEqualTo("mm/3 h");
        assertThat(resultat.get("dateRecuperationMeteo").asText()).isEqualTo(horloge.instant().toString());
        assertThat(resultat.get("donneesRecentes").asBoolean()).isTrue();
        assertThat(resultat.get("lue").asBoolean()).isFalse();
        assertThat(resultat.get("dateLecture").isNull()).isTrue();
        assertThat(resultat.has("keycloakId")).isFalse();
        assertThat(lectures.count()).isZero();
    }

    @Test void refuserLeDetailLaLectureEtLeFiltreDuneParcelleNonAutorisee() throws Exception {
        api.perform(get("/api/alertes/" + horsAcces.getId()).header("Authorization", "Bearer A"))
                .andExpect(status().isForbidden());
        api.perform(put("/api/alertes/" + horsAcces.getId() + "/lecture").header("Authorization", "Bearer A"))
                .andExpect(status().isForbidden());
        for (String chemin : List.of("/api/alertes/pagination", "/api/alertes/compteur")) {
            api.perform(get(chemin).param("parcelleId", "" + p3.getId()).header("Authorization", "Bearer A"))
                    .andExpect(status().isForbidden());
        }
        assertThat(lectures.count()).isZero();
    }

    @Test void garderLaLecturePersonnelleEtIgnorerUnCompteFourniDansLeCorps() throws Exception {
        api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer A")
                .contentType("application/json").content("{\"keycloakId\":\"compte-B\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lue").value(true));
        assertThat(detail(chaleur.getId(), "A").get("lue").asBoolean()).isTrue();
        assertThat(detail(chaleur.getId(), "B").get("lue").asBoolean()).isFalse();
        assertThat(detail(chaleur.getId(), "AGRONOME").get("lue").asBoolean()).isFalse();
        assertThat(detail(chaleur.getId(), "ADMIN").get("lue").asBoolean()).isFalse();
        compteur("A", 2);
        compteur("B", 3);
        compteur("AGRONOME", 2);
        compteur("ADMIN", 4);
        assertThat(lectures.findAll()).hasSize(1).allSatisfy(l -> assertThat(l.getKeycloakId()).isEqualTo("compte-A"));
    }

    @Test void rendreLeMarquageIdempotentEtConserverLaPremiereDate() throws Exception {
        String chemin = "/api/alertes/" + chaleur.getId() + "/lecture";
        api.perform(put(chemin).header("Authorization", "Bearer A")).andExpect(status().isOk());
        Instant premiere = horloge.instant();
        horloge.avancer(1);
        api.perform(put(chemin).header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.dateLecture").value(premiere.toString()));
        assertThat(lectures.count()).isEqualTo(1);
    }

    @Test void autoriserLaLectureParLadministrateurSansProfilLocalEtParLagronome() throws Exception {
        for (String compte : List.of("ADMIN", "AGRONOME")) {
            api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer " + compte))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.lue").value(true));
        }
        assertThat(lectures.count()).isEqualTo(2);
        assertThat(utilisateurs.findByKeycloakId("compte-ADMIN")).isEmpty();
        compteur("ADMIN", 3);
        compteur("AGRONOME", 1);
    }

    @Test void filtrerLaLecturePersonnelleAvantLeComptageEtLaPagination() throws Exception {
        api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer A"))
                .andExpect(status().isOk());
        api.perform(get("/api/alertes/pagination").param("lue", "true").param("taille", "1")
                .header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.contenu[0].id").value(chaleur.getId()));
        api.perform(get("/api/alertes/pagination").param("lue", "false").param("taille", "2")
                .header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3));
        api.perform(get("/api/alertes/pagination").param("lue", "true").header("Authorization", "Bearer B"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void combinerLesFiltresParcelleTypeNiveauEtEtat() throws Exception {
        api.perform(get("/api/alertes/pagination").param("parcelleId", "" + p1.getId())
                .param("type", "PLUIE_IMPORTANTE").param("niveau", "CRITIQUE").param("statut", "ACTIVE")
                .header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.contenu[0].id").value(pluie.getId()));
        api.perform(get("/api/alertes/compteur").param("parcelleId", "" + p1.getId())
                .header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.activesNonLues").value(2));
    }

    @Test void filtrerLesEtatsEffectifsSansAttendreLaTachePlanifiee() throws Exception {
        api.perform(get("/api/alertes/pagination").param("statut", "EXPIREE").header("Authorization", "Bearer A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        api.perform(get("/api/alertes/pagination").param("statut", "LEVEE").header("Authorization", "Bearer A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(detail(passee.getId(), "A").get("statut").asText()).isEqualTo("EXPIREE");
        // Un GET ne modifie pas le stockage : l'état présenté tient compte de l'échéance.
        assertThat(alertes.findById(passee.getId()).orElseThrow().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
        horloge.avancer(3600);
        compteur("A", 0);
        api.perform(get("/api/alertes/pagination").param("statut", "ACTIVE").header("Authorization", "Bearer A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void retirerImmediatementLesAlertesActivesQuandLesCoordonneesChangent() throws Exception {
        Exploitation exploitation = p1.getExploitation();
        exploitation.setLatitude(35.0);
        exploitations.saveAndFlush(exploitation);
        compteur("A", 1);
        assertThat(detail(chaleur.getId(), "A").get("statut").asText()).isEqualTo("EXPIREE");
        api.perform(get("/api/alertes/pagination").param("statut", "EXPIREE").param("parcelleId", "" + p1.getId())
                .header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        exploitation.setLatitude(null);
        exploitation.setLongitude(null);
        exploitations.saveAndFlush(exploitation);
        compteur("A", 1);
    }

    @Test void bloquerTousLesAccesEtCompteursApresRetraitIndividuelDaffectation() throws Exception {
        api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer A"))
                .andExpect(status().isOk());
        affectations.deleteById(affectationA1.getId());
        api.perform(get("/api/alertes/" + chaleur.getId()).header("Authorization", "Bearer A"))
                .andExpect(status().isForbidden());
        api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer A"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/alertes/pagination").param("parcelleId", "" + p1.getId()).header("Authorization", "Bearer A"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/alertes/compteur").param("parcelleId", "" + p1.getId()).header("Authorization", "Bearer A"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/alertes/pagination").header("Authorization", "Bearer A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].id").value(gel.getId()));
        compteur("A", 1);
        compteur("B", 3);
        assertThat(detail(chaleur.getId(), "B").get("lue").asBoolean()).isFalse();
    }

    @Test void filtrerLesDatesUtcInclusivesAvantLaPagination() throws Exception {
        Alerte minuit = alerte(p1, TypeAlerte.CHALEUR, NiveauAlerte.CRITIQUE, StatutAlerte.ACTIVE, Instant.parse("2026-11-13T00:00:00Z"));
        Alerte fin = alerte(p1, TypeAlerte.CHALEUR, NiveauAlerte.CRITIQUE, StatutAlerte.ACTIVE, Instant.parse("2026-11-13T23:59:59Z"));
        alerte(p1, TypeAlerte.CHALEUR, NiveauAlerte.CRITIQUE, StatutAlerte.ACTIVE, Instant.parse("2026-11-14T00:00:00Z"));
        api.perform(get("/api/alertes/pagination").param("dateDebut", "2026-11-13").param("dateFin", "2026-11-13")
                .param("taille", "1").header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.contenu[0].id").value(fin.getId()));
        api.perform(get("/api/alertes/pagination").param("dateDebut", "2026-11-13").param("dateFin", "2026-11-13")
                .param("taille", "1").param("page", "1").header("Authorization", "Bearer A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].id").value(minuit.getId()));
    }

    @Test void conserverUnOrdreStableEtLeTotalSurUnePageVide() throws Exception {
        api.perform(get("/api/alertes/pagination").param("taille", "2").header("Authorization", "Bearer A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].id").value(expiree.getId()))
                .andExpect(jsonPath("$.contenu[1].id").value(levee.getId())).andExpect(jsonPath("$.totalPages").value(3));
        api.perform(get("/api/alertes/pagination").param("page", "100").param("taille", "2")
                .header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", hasSize(0))).andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test void refuserLesParametresInvalides() throws Exception {
        for (String[] filtre : new String[][] {{"page", "-1"}, {"taille", "0"}, {"taille", "101"},
                {"page", "2147483647"}, {"parcelleId", "0"}, {"type", "INCONNU"}, {"niveau", "INCONNU"},
                {"statut", "INCONNU"}, {"lue", "inconnu"}, {"dateDebut", "2026-13-10"},
                {"dateDebut", "+10000-01-01"}, {"dateFin", "0000-01-01"}}) {
            api.perform(get("/api/alertes/pagination").param(filtre[0], filtre[1]).header("Authorization", "Bearer A"))
                    .andExpect(status().isBadRequest());
        }
        api.perform(get("/api/alertes/pagination").param("dateDebut", "2026-11-14").param("dateFin", "2026-11-13")
                .header("Authorization", "Bearer A")).andExpect(status().isBadRequest());
    }

    @Test void refuserLesIdentifiantsAbsentsEtLaConsultationAnonyme() throws Exception {
        for (String chemin : List.of("/api/alertes/pagination", "/api/alertes/compteur", "/api/alertes/" + chaleur.getId())) {
            api.perform(get(chemin)).andExpect(status().isUnauthorized());
        }
        api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture")).andExpect(status().isUnauthorized());
        api.perform(get("/api/alertes/999999").header("Authorization", "Bearer A")).andExpect(status().isNotFound());
        api.perform(put("/api/alertes/999999/lecture").header("Authorization", "Bearer A")).andExpect(status().isNotFound());
        api.perform(get("/api/alertes/0").header("Authorization", "Bearer A")).andExpect(status().isBadRequest());
        api.perform(get("/api/alertes/pagination").param("parcelleId", "999999").header("Authorization", "Bearer A"))
                .andExpect(status().isNotFound());
    }

    @Test void conserverLaDateSourceEtSignalerSaFraicheurSansInventerUneLevee() throws Exception {
        horloge.avancer(900);
        JsonNode resultat = detail(chaleur.getId(), "A");
        assertThat(resultat.get("donneesRecentes").asBoolean()).isFalse();
        assertThat(resultat.get("statut").asText()).isEqualTo("ACTIVE");
        compteur("A", 3);
    }

    @Test void garderLaLectureQuandLaMemeAlerteEstReevaluee() throws Exception {
        api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer A"))
                .andExpect(status().isOk());
        calcul.evaluer(p1.getId(), MeteoResponse.builder().parcelleId(p1.getId()).exploitationId(p1.getExploitation().getId())
                .latitude(34.74).longitude(10.76).statut(StatutMeteo.DISPONIBLE)
                .donnees(DonneesMeteoResponse.builder().recupereesLe(horloge.instant()).previsions(List.of(
                        PrevisionMeteoResponse.builder().datePrevision(chaleur.getDatePrevision()).temperatureCelsius(40.0).build())).build())
                .build());
        JsonNode resultat = detail(chaleur.getId(), "A");
        assertThat(resultat.get("lue").asBoolean()).isTrue();
        assertThat(resultat.get("niveau").asText()).isEqualTo("CRITIQUE");
        assertThat(lectures.count()).isEqualTo(1);
    }

    @Test void accepterDeuxMarquagesSimultanesSansDoublon() throws Exception {
        CountDownLatch depart = new CountDownLatch(1);
        try (var executants = Executors.newFixedThreadPool(2)) {
            var premier = executants.submit(() -> {
                depart.await();
                api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer A"))
                        .andExpect(status().isOk());
                return true;
            });
            var second = executants.submit(() -> {
                depart.await();
                api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer A"))
                        .andExpect(status().isOk());
                return true;
            });
            depart.countDown();
            assertThat(premier.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(lectures.count()).isEqualTo(1);
        compteur("A", 2);
    }

    @Test void appliquerLaDesactivationDuCompteAvecLeMemeToken() throws Exception {
        UserRepresentation desactive = new UserRepresentation();
        desactive.setEnabled(false);
        when(comptes.consulter("compte-A")).thenReturn(desactive);
        api.perform(get("/api/alertes/compteur").header("Authorization", "Bearer A")).andExpect(status().isUnauthorized());
        api.perform(put("/api/alertes/" + chaleur.getId() + "/lecture").header("Authorization", "Bearer A"))
                .andExpect(status().isUnauthorized());
        assertThat(lectures.count()).isZero();
    }

    @Test void conserverLaMemeDateDeLectureApresArrondiDeLaBase() throws Exception {
        when(meteoHorloge.instant()).thenReturn(Instant.parse("2026-11-12T12:00:00.123456789Z"));
        String chemin = "/api/alertes/" + chaleur.getId() + "/lecture";
        String premiere = api.perform(put(chemin).header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String seconde = api.perform(put(chemin).header("Authorization", "Bearer A")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(premiere).get("dateLecture")).isEqualTo(json.readTree(seconde).get("dateLecture"));
        assertThat(lectures.count()).isEqualTo(1);
    }
}
