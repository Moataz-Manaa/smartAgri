package com.example.backend;

import com.example.backend.entity.*;
import com.example.backend.enums.*;
import com.example.backend.repository.*;
import com.example.backend.service.CompteKeycloakService;
import com.example.backend.service.TokenService;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:smartagri-tableau;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class TableauDeBordTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired ParcelleRepository parcelles;
    @Autowired ExploitationRepository exploitations;
    @Autowired UtilisateurRepository utilisateurs;
    @Autowired AffectationRepository affectations;
    @Autowired CampagneRepository campagnes;
    @Autowired PlantationRepository plantations;
    @Autowired CultureRepository cultures;
    @Autowired VarieteRepository varietes;
    @Autowired AlerteRepository alertes;
    @Autowired LectureAlerteRepository lectures;
    @MockitoBean Clock meteoHorloge;
    @MockitoBean TokenService tokens;
    @MockitoBean CompteKeycloakService comptes;
    final Instant maintenant = Instant.parse("2026-11-14T10:00:00Z");
    Parcelle p1, p2, p3;
    Campagne campagne, ancienne, vide;
    Plantation pl1, pl2;
    Utilisateur auteur;
    Affectation affectationA1;
    Alerte alerteP1;

    @BeforeEach void preparer() throws Exception {
        for (String table : List.of("observations", "irrigations", "fertilisations", "traitements", "recoltes")) {
            jdbc.update("DELETE FROM " + table);
        }
        lectures.deleteAll(); alertes.deleteAll(); plantations.deleteAll(); campagnes.deleteAll();
        affectations.deleteAll(); parcelles.deleteAll(); exploitations.deleteAll(); utilisateurs.deleteAll();
        varietes.deleteAll(); cultures.deleteAll();
        when(meteoHorloge.instant()).thenReturn(maintenant);
        p1 = parcelle("1"); p2 = parcelle("2"); p3 = parcelle("3");
        identite("ADMIN", Role.ADMIN);
        auteur = identite("A", Role.AGRICULTEUR);
        Utilisateur b = identite("B", Role.AGRICULTEUR);
        Utilisateur agro = identite("AGRO", Role.AGRONOME);
        identite("SANS", Role.AGRICULTEUR);
        affectationA1 = affecter(auteur, p1); affecter(auteur, p2);
        affecter(b, p1); affecter(b, p3); affecter(agro, p1);
        campagne = campagne("Novembre", "2026-11-01", "2026-11-30", List.of(p1,p2,p3));
        ancienne = campagne("Octobre", "2026-10-01", "2026-10-31", List.of(p1));
        vide = campagne("Sans plantation", "2026-11-01", "2026-11-30", List.of(p1));
        Culture culture = cultures.saveAndFlush(Culture.builder().nom("Olivier").nomNormalise("olivier").build());
        Variete variete = varietes.saveAndFlush(Variete.builder().nom("Chemlali").nomNormalise("chemlali").culture(culture).build());
        pl1 = plantation(p1, campagne, variete);
        pl2 = plantation(p2, campagne, variete);
        Plantation pl3 = plantation(p3, campagne, variete);
        Plantation old = plantation(p1, ancienne, variete);
        irrigation(pl1, "2026-11-01", "500", "LITRE", "0.5");
        irrigation(pl1, "2026-11-30", "2", "METRE_CUBE", "2");
        recolte(pl1, "100", "KILOGRAMME", "100");
        recolte(pl1, "1", "TONNE", "1000");
        jdbc.update("INSERT INTO observations (plantation_id,auteur_id,date_observation,description,version) VALUES (?,?,?,'Feuilles observées',0)", pl1.getId(), auteur.getId(), java.sql.Date.valueOf("2026-11-15"));
        for (String table : List.of("fertilisations", "traitements")) {
            String date = table.equals("fertilisations") ? "date_fertilisation" : "date_traitement";
            String colonneCible = table.equals("traitements") ? ",cible" : "";
            String valeurCible = table.equals("traitements") ? ",'Symptômes observés'" : "";
            jdbc.update("INSERT INTO " + table + " (plantation_id,auteur_id," + date + ",nom_produit,quantite,unite,version" + colonneCible + ") VALUES (?,?,?,'Produit de test',1,'KILOGRAMME',0" + valeurCible + ")", pl1.getId(), auteur.getId(), java.sql.Date.valueOf("2026-11-16"));
        }
        irrigation(pl2, "2026-11-14", "3", "METRE_CUBE", "3"); recolte(pl2, "200", "KILOGRAMME", "200");
        irrigation(pl3, "2026-11-14", "9", "METRE_CUBE", "9"); recolte(pl3, "9", "TONNE", "9000");
        irrigation(old, "2026-10-31", "99", "METRE_CUBE", "99");
        alerteP1 = alerte(p1, maintenant.plusSeconds(3600));
        alerte(p2, maintenant.plusSeconds(7200)); alerte(p3, maintenant.plusSeconds(10800));
    }

    Parcelle parcelle(String code) {
        Exploitation e = exploitations.saveAndFlush(Exploitation.builder().nom("Ferme " + code).code("EXP-" + code).latitude(34.0).longitude(10.0).build());
        return parcelles.saveAndFlush(Parcelle.builder().code("PAR-"+code).nom("Parcelle "+code).superficie(5.0).exploitation(e).build());
    }
    Utilisateur identite(String nom, Role role) throws Exception {
        String id = "compte-"+nom;
        when(tokens.verifier(nom)).thenReturn(new JWTClaimsSet.Builder().subject(id).build());
        UserRepresentation compte = new UserRepresentation(); compte.setEnabled(true);
        when(comptes.consulter(id)).thenReturn(compte); when(comptes.consulterRole(id)).thenReturn(role);
        if(role == Role.ADMIN) return null;
        return utilisateurs.saveAndFlush(Utilisateur.builder().keycloakId(id).nom(nom).prenom("Test").email(nom+"@example.test").build());
    }
    Affectation affecter(Utilisateur u, Parcelle p) {
        return affectations.saveAndFlush(Affectation.builder().utilisateur(u).parcelle(p).build());
    }
    Campagne campagne(String nom, String debut, String fin, List<Parcelle> ps) {
        return campagnes.saveAndFlush(Campagne.builder().nom(nom).nomNormalise(nom.toLowerCase())
                .dateDebut(LocalDate.parse(debut)).dateFin(LocalDate.parse(fin)).statut(StatutCampagne.EN_COURS)
                .parcelles(new ArrayList<>(ps)).build());
    }
    Plantation plantation(Parcelle p, Campagne c, Variete v) {
        // Un olivier planté avant la campagne reste suivi pendant celle-ci.
        return plantations.saveAndFlush(Plantation.builder().parcelle(p).campagne(c).variete(v)
                .datePlantation(LocalDate.parse("2020-01-01")).superficie(1.0).statut(StatutPlantation.EN_COURS).build());
    }
    void irrigation(Plantation pl, String date, String volume, String unite, String normalise) {
        jdbc.update("INSERT INTO irrigations (plantation_id,auteur_id,date_irrigation,volume,unite,volume_metres_cubes,version) VALUES (?,?,?,?,?,?,0)",
                pl.getId(), auteur.getId(), java.sql.Date.valueOf(date), new java.math.BigDecimal(volume), unite, new java.math.BigDecimal(normalise));
    }
    void recolte(Plantation pl, String quantite, String unite, String normalise) {
        jdbc.update("INSERT INTO recoltes (plantation_id,auteur_id,date_recolte,quantite,unite,quantite_kilogrammes,version) VALUES (?,?,?,?,?,?,0)",
                pl.getId(), auteur.getId(), java.sql.Date.valueOf("2026-11-14"), new java.math.BigDecimal(quantite), unite, new java.math.BigDecimal(normalise));
    }
    Alerte alerte(Parcelle p, Instant date) {
        return alertes.saveAndFlush(Alerte.builder().parcelle(p).type(TypeAlerte.CHALEUR).niveau(NiveauAlerte.CRITIQUE)
                .statut(StatutAlerte.ACTIVE).valeur(40.0).seuil(40.0).unite("°C").message("Chaleur prévue")
                .latitude(34.0).longitude(10.0).datePrevision(date).debutPeriode(date)
                .dateDetection(maintenant).derniereVerification(maintenant).dateRecuperationMeteo(maintenant).build());
    }
    MockHttpServletRequestBuilder requete(String bloc, String compte) {
        return get("/api/tableau-de-bord/"+bloc).header("Authorization", "Bearer "+compte)
                .param("dateDebut","2026-11-01").param("dateFin","2026-11-30");
    }

    @Test void agregerSansMultiplierLesParcellesPartageesEtNormaliserLesUnites() throws Exception {
        api.perform(requete("synthese","A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreParcelles").value(2)).andExpect(jsonPath("$.nombreCampagnes").value(2))
                .andExpect(jsonPath("$.nombrePlantations").value(2)).andExpect(jsonPath("$.nombreActivites").value(9))
                .andExpect(jsonPath("$.volumeIrrigationMetresCubes").value(5.5))
                .andExpect(jsonPath("$.quantiteRecolteeKilogrammes").value(1300));
    }
    @Test void donnerUneVueGlobaleALadminSansProfilNiAffectation() throws Exception {
        api.perform(requete("synthese","ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreParcelles").value(3)).andExpect(jsonPath("$.nombrePlantations").value(3))
                .andExpect(jsonPath("$.nombreActivites").value(11)).andExpect(jsonPath("$.volumeIrrigationMetresCubes").value(14.5))
                .andExpect(jsonPath("$.quantiteRecolteeKilogrammes").value(10300));
    }
    @Test void limiterLagronomeAuxParcellesAffectees() throws Exception {
        api.perform(requete("synthese","AGRO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreParcelles").value(1)).andExpect(jsonPath("$.nombrePlantations").value(1))
                .andExpect(jsonPath("$.nombreActivites").value(7)).andExpect(jsonPath("$.volumeIrrigationMetresCubes").value(2.5))
                .andExpect(jsonPath("$.quantiteRecolteeKilogrammes").value(1100));
    }
    @Test void combinerPeriodeParcelleEtCampagneAvecBornesInclusives() throws Exception {
        api.perform(requete("synthese","A").param("parcelleId",p1.getId().toString()).param("campagneId",campagne.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombreCampagnes").value(1))
                .andExpect(jsonPath("$.volumeIrrigationMetresCubes").value(2.5));
        api.perform(get("/api/tableau-de-bord/synthese").header("Authorization","Bearer A")
                        .param("dateDebut","2026-11-14").param("dateFin","2026-11-14"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombreActivites").value(4))
                .andExpect(jsonPath("$.volumeIrrigationMetresCubes").value(3))
                .andExpect(jsonPath("$.quantiteRecolteeKilogrammes").value(1300));
    }
    @Test void conserverUneCampagneSansPlantationSansInventerDactivite() throws Exception {
        api.perform(requete("synthese","A").param("campagneId",vide.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreParcelles").value(1)).andExpect(jsonPath("$.nombreCampagnes").value(1))
                .andExpect(jsonPath("$.nombrePlantations").value(0)).andExpect(jsonPath("$.nombreActivites").value(0))
                .andExpect(jsonPath("$.volumeIrrigationMetresCubes").value(0));
    }
    @Test void renvoyerDesZerosEtDesListesVidesSansAffectation() throws Exception {
        api.perform(requete("synthese","SANS")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreParcelles").value(0)).andExpect(jsonPath("$.nombreCampagnes").value(0))
                .andExpect(jsonPath("$.nombrePlantations").value(0)).andExpect(jsonPath("$.quantiteRecolteeKilogrammes").value(0));
        api.perform(requete("activites","SANS")).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));
        api.perform(requete("alertes","SANS")).andExpect(status().isOk()).andExpect(jsonPath("$.actives").value(0));
    }
    @Test void bornerEtTrierLesActivitesRecentesAvecLeurContexte() throws Exception {
        api.perform(requete("activites","A")).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(5)))
                .andExpect(jsonPath("$[0].type").value("IRRIGATION")).andExpect(jsonPath("$[0].date").value("2026-11-30"))
                .andExpect(jsonPath("$[1].type").value("FERTILISATION")).andExpect(jsonPath("$[2].type").value("TRAITEMENT"))
                .andExpect(jsonPath("$[*].parcelleCode",not(hasItem("PAR-3"))))
                .andExpect(jsonPath("$[0].plantationId").value(pl1.getId())).andExpect(jsonPath("$[0].campagneNom").value("Novembre"));
    }
    @Test void limiterLesActivitesParCampagneEtParcelle() throws Exception {
        api.perform(requete("activites","A").param("parcelleId",p2.getId().toString()).param("campagneId",campagne.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(2)))
                .andExpect(jsonPath("$[*].parcelleCode",everyItem(is("PAR-2"))));
    }
    @Test void compterLesAlertesActivesEtLeurLecturePersonnelle() throws Exception {
        api.perform(put("/api/alertes/"+alerteP1.getId()+"/lecture").header("Authorization","Bearer A")).andExpect(status().isOk());
        api.perform(requete("alertes","A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.actives").value(2)).andExpect(jsonPath("$.activesNonLues").value(1));
        api.perform(requete("alertes","AGRO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.actives").value(1)).andExpect(jsonPath("$.activesNonLues").value(1));
        api.perform(requete("alertes","ADMIN")).andExpect(status().isOk()).andExpect(jsonPath("$.actives").value(3));
    }
    @Test void appliquerLaPeriodeUtcAuxEcheancesEtLesParcellesDeCampagne() throws Exception {
        alerte(p1, Instant.parse("2026-11-15T00:00:00Z"));
        api.perform(get("/api/tableau-de-bord/alertes").header("Authorization","Bearer A")
                        .param("dateDebut","2026-11-14").param("dateFin","2026-11-14"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actives").value(2));
        api.perform(requete("alertes","A").param("campagneId",vide.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actives").value(2));
    }
    @Test void exclureUneEcheancePasseeEtUnPointModifieSansAttendreLaPlanification() throws Exception {
        when(meteoHorloge.instant()).thenReturn(maintenant.plusSeconds(3600));
        api.perform(requete("alertes","A")).andExpect(status().isOk()).andExpect(jsonPath("$.actives").value(1));
        Exploitation e = p2.getExploitation(); e.setLatitude(0.0); exploitations.saveAndFlush(e);
        api.perform(requete("alertes","A")).andExpect(status().isOk()).andExpect(jsonPath("$.actives").value(0));
    }
    @Test void reevaluerLesAffectationsSurLesTroisBlocs() throws Exception {
        affectations.deleteById(affectationA1.getId());
        api.perform(requete("synthese","A")).andExpect(status().isOk()).andExpect(jsonPath("$.nombreParcelles").value(1))
                .andExpect(jsonPath("$.quantiteRecolteeKilogrammes").value(200));
        api.perform(requete("activites","A")).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(2)));
        api.perform(requete("alertes","A")).andExpect(status().isOk()).andExpect(jsonPath("$.actives").value(1));
        for (String bloc : List.of("synthese","activites","alertes")) {
            api.perform(requete(bloc,"A").param("parcelleId",p1.getId().toString())).andExpect(status().isForbidden());
        }
    }
    @Test void refuserUnFiltreNonAutoriseMemeSansActiviteDansLaPeriode() throws Exception {
        Campagne privee = campagne("Privée","2026-01-01","2026-01-31",List.of(p3));
        for (String bloc : List.of("synthese","activites","alertes")) {
            api.perform(requete(bloc,"A").param("parcelleId",p3.getId().toString())).andExpect(status().isForbidden());
            api.perform(requete(bloc,"A").param("campagneId",privee.getId().toString())).andExpect(status().isForbidden());
            api.perform(requete(bloc,"A").param("campagneId","999999")).andExpect(status().isNotFound());
            api.perform(requete(bloc,"A").param("parcelleId","999999")).andExpect(status().isNotFound());
        }
    }
    @Test void validerDatesIdentifiantsEtAbsenceDeSessionSurChaqueBloc() throws Exception {
        for (String bloc : List.of("synthese","activites","alertes")) {
            api.perform(get("/api/tableau-de-bord/"+bloc)).andExpect(status().isUnauthorized());
            for(String id : List.of("0","-1","texte")) {
                api.perform(requete(bloc,"A").param("parcelleId",id)).andExpect(status().isBadRequest());
            }
            api.perform(get("/api/tableau-de-bord/"+bloc).header("Authorization","Bearer A")
                    .param("dateDebut","2026-11-30").param("dateFin","2026-11-01")).andExpect(status().isBadRequest());
            api.perform(get("/api/tableau-de-bord/"+bloc).header("Authorization","Bearer A")
                    .param("dateDebut","2026-02-30")).andExpect(status().isBadRequest());
        }
    }
    @Test void refuserUnCompteDesactiveMemeAvecUnTokenEncoreValide() throws Exception {
        UserRepresentation compte = new UserRepresentation(); compte.setEnabled(false);
        when(comptes.consulter("compte-A")).thenReturn(compte);
        for (String bloc : List.of("synthese","activites","alertes")) api.perform(requete(bloc,"A")).andExpect(status().isUnauthorized());
    }
    @Test void choisirLesTrenteDerniersJoursUtcParDefaut() throws Exception {
        api.perform(get("/api/tableau-de-bord/synthese").header("Authorization","Bearer A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dateDebut").value("2026-10-16"))
                .andExpect(jsonPath("$.dateFin").value("2026-11-14"));
    }
}
