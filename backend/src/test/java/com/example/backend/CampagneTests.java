package com.example.backend;

import com.example.backend.entity.Campagne;
import com.example.backend.entity.Exploitation;
import com.example.backend.entity.Parcelle;
import com.example.backend.entity.Utilisateur;
import com.example.backend.entity.Affectation;
import com.example.backend.enums.Role;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.repository.CampagneRepository;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.repository.ExploitationRepository;
import com.example.backend.repository.UtilisateurRepository;
import com.example.backend.repository.AffectationRepository;
import com.example.backend.service.CompteKeycloakService;
import com.example.backend.service.TokenService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CampagneTests {
    @Autowired MockMvc api;
    @Autowired CampagneRepository campagnes;
    @Autowired ParcelleRepository parcelles;
    @Autowired ExploitationRepository exploitations;
    @Autowired UtilisateurRepository utilisateurs;
    @Autowired AffectationRepository affectations;
    private Parcelle parcelle;
    @MockitoBean TokenService tokens;
    @MockitoBean CompteKeycloakService comptes;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired com.example.backend.repository.PlantationRepository plantations;

    @BeforeEach
    void preparer() throws Exception {
        plantations.deleteAll();
        campagnes.deleteAll();
        affectations.deleteAll();
        parcelles.deleteAll();
        exploitations.deleteAll();
        utilisateurs.deleteAll();
        Exploitation exploitation = exploitations.saveAndFlush(Exploitation.builder()
                .code("EXP-CAMPAGNE-TEST").nom("Exploitation").superficie(20.0).build());
        parcelle = parcelles.saveAndFlush(Parcelle.builder().code("PAR-CAMPAGNE-TEST")
                .nom("Parcelle").superficie(5.0).exploitation(exploitation).build());
        for (Role role : Role.values()) {
            String id = "campagne-" + role.name();
            when(tokens.verifier(role.name())).thenReturn(new JWTClaimsSet.Builder().subject(id).build());
            UserRepresentation compte = new UserRepresentation();
            compte.setEnabled(true);
            when(comptes.consulter(id)).thenReturn(compte);
            when(comptes.consulterRole(id)).thenReturn(role);
            if (role != Role.ADMIN) {
                Utilisateur utilisateur = utilisateurs.saveAndFlush(Utilisateur.builder().keycloakId(id)
                        .nom(role.name()).prenom("Test").email(id + "@example.test").build());
                affectations.saveAndFlush(Affectation.builder().utilisateur(utilisateur).parcelle(parcelle).build());
            }
        }
    }

    @Test
    void creerUneCampagnePlanifieeEtConserverDatesIso() throws Exception {
        String contenu = "{\"nom\":\"  Campagne 2027  \",\"dateDebut\":\"2027-01-01\","
                + "\"dateFin\":\"2027-12-31\",\"statut\":\"CLOTUREE\",\"parcelleIds\":[" + parcelle.getId() + "]}";
        api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(contenu))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nom").value("Campagne 2027"))
                .andExpect(jsonPath("$.dateDebut").value("2027-01-01"))
                .andExpect(jsonPath("$.dateFin").value("2027-12-31"))
                .andExpect(jsonPath("$.statut").value("PLANIFIEE"))
                .andExpect(jsonPath("$.nomNormalise").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void datesObligatoiresEtFinStrictementApresDebut() throws Exception {
        for (String fin : new String[]{"2027-01-01", "2026-12-31", "date-invalide"}) {
            api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                            .contentType("application/json").content(demande("Dates", "2027-01-01", fin)))
                    .andExpect(status().isBadRequest());
        }
        api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content("{\"nom\":\"Dates\"}"))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(demande("  ", "2027-01-01", "2027-12-31")))
                .andExpect(status().isBadRequest());
        assertThat(campagnes.count()).isZero();
    }

    @Test
    void cycleStatutsEtClotureLectureSeule() throws Exception {
        long id = creer("Cycle", "2027-01-01").get("id").asLong();
        statut(id, "EN_COURS", 200);
        statut(id, "EN_COURS", 200);
        api.perform(put("/api/campagnes/" + id).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(demande("Cycle modifié", "2027-01-01", "2027-11-30")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("EN_COURS"));
        statut(id, "CLOTUREE", 200);
        statut(id, "CLOTUREE", 200);
        statut(id, "EN_COURS", 409);
        api.perform(put("/api/campagnes/" + id).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(demande("Interdit", "2027-01-01", "2027-12-31")))
                .andExpect(status().isConflict());
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.nom").value("Cycle modifié"));
    }

    @Test
    void refuserSautRetourEtStatutInvalide() throws Exception {
        long id = creer("Transitions", "2027-01-01").get("id").asLong();
        statut(id, "CLOTUREE", 409);
        statut(id, "INCONNU", 400);
        api.perform(put("/api/campagnes/" + id + "/statut").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        statut(id, "EN_COURS", 200);
        statut(id, "PLANIFIEE", 409);
    }

    @Test
    void seulsAdministrateursCreentModifientEtChangentStatut() throws Exception {
        long id = creer("Droits", "2027-01-01").get("id").asLong();
        for (String role : new String[]{"AGRICULTEUR", "AGRONOME"}) {
            for (String chemin : new String[]{"/api/campagnes", "/api/campagnes/pagination", "/api/campagnes/" + id}) {
                api.perform(get(chemin).header("Authorization", "Bearer " + role)).andExpect(status().isOk());
            }
            api.perform(post("/api/campagnes").header("Authorization", "Bearer " + role)
                            .contentType("application/json").content(demande("Interdit", "2027-01-01", "2027-12-31")))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/campagnes/" + id).header("Authorization", "Bearer " + role)
                            .contentType("application/json").content(demande("Interdit", "2027-01-01", "2027-12-31")))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/campagnes/" + id + "/statut").header("Authorization", "Bearer " + role)
                            .contentType("application/json").content("{\"statut\":\"EN_COURS\"}"))
                    .andExpect(status().isForbidden());
        }
        api.perform(get("/api/campagnes")).andExpect(status().isUnauthorized());
    }

    @Test
    void doublonsCreationEtRenommage() throws Exception {
        JsonNode premiere = creer("Campagne 2027", "2027-01-01");
        api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(demande(" CAMPAGNE 2027 ", "2027-01-01", "2027-12-31")))
                .andExpect(status().isConflict());
        long autreId = creer("Autre", "2027-01-01").get("id").asLong();
        api.perform(put("/api/campagnes/" + autreId).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(demande("campagne 2027", "2027-01-01", "2027-12-31")))
                .andExpect(status().isConflict());
        Campagne copie = Campagne.builder().nom("Copie").nomNormalise("campagne 2027")
                .dateDebut(java.time.LocalDate.of(2027, 1, 1)).dateFin(java.time.LocalDate.of(2027, 12, 31))
                .statut(StatutCampagne.PLANIFIEE).build();
        assertThatThrownBy(() -> campagnes.saveAndFlush(copie)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(campagnes.findById(premiere.get("id").asLong())).isPresent();
    }

    @Test
    void filtreAvantPaginationEtTriStable() throws Exception {
        long ancienne = creer("Ancienne", "2026-01-01").get("id").asLong();
        long premiere = creer("Première", "2027-01-01").get("id").asLong();
        long seconde = creer("Seconde", "2027-01-01").get("id").asLong();
        statut(premiere, "EN_COURS", 200);
        statut(seconde, "EN_COURS", 200);
        api.perform(get("/api/campagnes/pagination").param("statut", "EN_COURS").param("taille", "1")
                        .header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.contenu[0].id").value(seconde));
        api.perform(get("/api/campagnes/pagination").param("statut", "EN_COURS").param("taille", "1").param("page", "1")
                        .header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].id").value(premiere));
        api.perform(get("/api/campagnes").param("statut", "PLANIFIEE").header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(ancienne));
        api.perform(get("/api/campagnes/pagination").param("taille", "101").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isBadRequest());
        api.perform(get("/api/campagnes/pagination").param("statut", "INCONNU").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isBadRequest());
        api.perform(get("/api/campagnes/999999").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modificationConcurrenteNeReouvrePasUneCampagneCloturee() throws Exception {
        long id = creer("Concurrence", "2027-01-01").get("id").asLong();
        statut(id, "EN_COURS", 200);
        Campagne cloture = campagnes.findById(id).orElseThrow();
        Campagne ancienneVersion = campagnes.findById(id).orElseThrow();
        cloture.setStatut(StatutCampagne.CLOTUREE);
        campagnes.saveAndFlush(cloture);
        ancienneVersion.setDescription("Modification obsolète");
        assertThatThrownBy(() -> campagnes.saveAndFlush(ancienneVersion))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(campagnes.findById(id).orElseThrow().getStatut()).isEqualTo(StatutCampagne.CLOTUREE);
    }

    @Test
    void campagnePartageeNeReveleQueLesParcellesAutorisees() throws Exception {
        Parcelle privee = creerParcellePrivee();
        affecterAgronome(privee);
        JsonNode campagne = creerAvecParcelles("Partagée", List.of(parcelle.getId(), privee.getId()));
        long id = campagne.get("id").asLong();
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcelles", hasSize(2)));
        String reponse = api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcelleIds", contains(parcelle.getId().intValue())))
                .andExpect(jsonPath("$.parcelles", hasSize(1)))
                .andExpect(jsonPath("$.parcelles[0].id").value(parcelle.getId())).andReturn().getResponse().getContentAsString();
        assertThat(reponse).doesNotContain("PAR-PRIVEE", "Exploitation privée", "Parcelle privée");
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcelles", hasSize(2)));
        api.perform(get("/api/campagnes/pagination").header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].parcelles", hasSize(1)));
    }

    @Test
    void campagnesFiltreesAvantComptageSansDoublons() throws Exception {
        Parcelle privee = creerParcellePrivee();
        affecterAgronome(privee);
        creerAvecParcelles("Partagée", List.of(parcelle.getId(), privee.getId()));
        long idPrive = creerAvecParcelles("Privée", List.of(privee.getId())).get("id").asLong();
        statut(idPrive, "EN_COURS", 200);
        api.perform(get("/api/campagnes/pagination").param("taille", "1").header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.perform(get("/api/campagnes/pagination").header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        api.perform(get("/api/campagnes/pagination").param("statut", "EN_COURS")
                        .header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        api.perform(get("/api/campagnes").param("statut", "EN_COURS").header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        api.perform(get("/api/campagnes/" + idPrive).header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void retraitAffectationCoupeAccesCampagneSansRetirerLesAutresAcces() throws Exception {
        long id = creerAvecParcelles("Retrait", List.of(parcelle.getId())).get("id").asLong();
        Long utilisateurId = utilisateurs.findByKeycloakId("campagne-AGRICULTEUR").orElseThrow().getId();
        for (Affectation affectation : affectations.findByParcelle_IdOrderByIdDesc(parcelle.getId())) {
            if (affectation.getUtilisateur().getId().equals(utilisateurId)) {
                affectations.deleteById(affectation.getId());
            }
        }
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/campagnes/pagination").header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk());
    }

    @Test
    void modifierLesParcellesDeCampagneRetireAccesSansChangerLesAffectations() throws Exception {
        long id = creerAvecParcelles("Parcelles", List.of(parcelle.getId())).get("id").asLong();
        Parcelle privee = creerParcellePrivee();
        api.perform(put("/api/campagnes/" + id).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "nom", "Parcelles", "dateDebut", "2027-01-01", "dateFin", "2027-12-31",
                                "parcelleIds", List.of(privee.getId())))))
                .andExpect(status().isOk());
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/parcelles/" + parcelle.getId()).header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk());
    }

    @Test
    void selectionParcellesObligatoireValideEtSansDoublons() throws Exception {
        List<List<Long>> invalides = new java.util.ArrayList<>();
        invalides.add(List.of());
        invalides.add(List.of(-1L));
        invalides.add(List.of(parcelle.getId(), parcelle.getId()));
        invalides.add(java.util.Arrays.asList((Long) null));
        for (List<Long> ids : invalides) {
            api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                            .contentType("application/json").content(json.writeValueAsString(Map.of(
                                    "nom", "Invalide", "dateDebut", "2027-01-01", "dateFin", "2027-12-31", "parcelleIds", ids))))
                    .andExpect(status().isBadRequest());
        }
        api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN").contentType("application/json")
                        .content("{\"nom\":\"Invalide\",\"dateDebut\":\"2027-01-01\",\"dateFin\":\"2027-12-31\"}"))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("nom", "Absente", "dateDebut", "2027-01-01",
                                "dateFin", "2027-12-31", "parcelleIds", List.of(999999L)))))
                .andExpect(status().isNotFound());
        assertThat(campagnes.count()).isZero();
    }

    @Test
    void campagneAncienneClotureeRattachementInitialSeulement() throws Exception {
        Campagne ancienne = ancienneCampagne(StatutCampagne.CLOTUREE);
        long id = ancienne.getId();
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcelles", hasSize(0)));
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isForbidden());
        api.perform(put("/api/campagnes/" + id + "/parcelles").header("Authorization", "Bearer AGRICULTEUR")
                        .contentType("application/json").content(json.writeValueAsString(Map.of("parcelleIds", List.of(parcelle.getId())))))
                .andExpect(status().isForbidden());
        api.perform(put("/api/campagnes/" + id + "/parcelles").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(Map.of("parcelleIds", List.of(parcelle.getId())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.nom").value("Ancienne campagne"));
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk());
        api.perform(put("/api/campagnes/" + id + "/parcelles").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(Map.of("parcelleIds", List.of(parcelle.getId())))))
                .andExpect(status().isConflict());
        api.perform(put("/api/campagnes/" + id).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(demande("Interdit", "2027-01-01", "2027-12-31")))
                .andExpect(status().isConflict());
    }

    @Test
    void campagneAncienneSansParcellesCacheeEtStatutBloqueAvantRattachement() throws Exception {
        Campagne ancienne = ancienneCampagne(StatutCampagne.PLANIFIEE);
        api.perform(get("/api/campagnes").header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        statut(ancienne.getId(), "EN_COURS", 409);
        api.perform(put("/api/campagnes/" + ancienne.getId()).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(demande("Ancienne campagne", "2027-01-01", "2027-12-31")))
                .andExpect(status().isOk());
        statut(ancienne.getId(), "EN_COURS", 200);
    }

    private Campagne ancienneCampagne(StatutCampagne statut) {
        return campagnes.saveAndFlush(Campagne.builder().nom("Ancienne campagne").nomNormalise("ancienne campagne")
                .dateDebut(java.time.LocalDate.of(2027, 1, 1)).dateFin(java.time.LocalDate.of(2027, 12, 31))
                .statut(statut).build());
    }

    private Parcelle creerParcellePrivee() {
        Exploitation exploitation = exploitations.saveAndFlush(Exploitation.builder().nom("Exploitation privée")
                .code("EXP-PRIVEE").superficie(10.0).build());
        return parcelles.saveAndFlush(Parcelle.builder().nom("Parcelle privée").code("PAR-PRIVEE")
                .superficie(5.0).exploitation(exploitation).build());
    }

    private void affecterAgronome(Parcelle autre) {
        Utilisateur utilisateur = utilisateurs.findByKeycloakId("campagne-AGRONOME").orElseThrow();
        affectations.saveAndFlush(Affectation.builder().utilisateur(utilisateur).parcelle(autre).build());
    }

    private JsonNode creerAvecParcelles(String nom, List<Long> ids) throws Exception {
        return json.readTree(api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "nom", nom, "dateDebut", "2027-01-01", "dateFin", "2027-12-31", "parcelleIds", ids))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode creer(String nom, String debut) throws Exception {
        String fin = debut.substring(0, 4) + "-12-31";
        return json.readTree(api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(demande(nom, debut, fin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String demande(String nom, String debut, String fin) throws Exception {
        return json.writeValueAsString(Map.of("nom", nom, "dateDebut", debut, "dateFin", fin,
                "parcelleIds", List.of(parcelle.getId())));
    }

    private void statut(long id, String statut, int attendu) throws Exception {
        api.perform(put("/api/campagnes/" + id + "/statut").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(Map.of("statut", statut))))
                .andExpect(status().is(attendu));
    }
}
