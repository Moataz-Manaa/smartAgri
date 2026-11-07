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
class PlantationTests {
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

    @Autowired com.example.backend.repository.CultureRepository cultures;
    @Autowired com.example.backend.repository.VarieteRepository varietes;
    private long varieteId;
    private long campagneId;

    @Autowired com.example.backend.repository.ObservationRepository observations;

    @Autowired com.example.backend.repository.IrrigationRepository irrigations;

    @Autowired com.example.backend.repository.FertilisationRepository fertilisations;
    @Autowired com.example.backend.repository.TraitementRepository traitements;

    @BeforeEach
    void preparer() throws Exception {
        traitements.deleteAll();
        fertilisations.deleteAll();
        irrigations.deleteAll();
        observations.deleteAll();
        plantations.deleteAll();
        campagnes.deleteAll();
        varietes.deleteAll();
        cultures.deleteAll();
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
        var culture = cultures.saveAndFlush(com.example.backend.entity.Culture.builder()
                .nom("Olivier").nomNormalise("olivier").build());
        varieteId = varietes.saveAndFlush(com.example.backend.entity.Variete.builder()
                .nom("Chemlali").nomNormalise("chemlali").culture(culture).build()).getId();
        campagneId = creerAvecParcelles("Suivi 2027", List.of(parcelle.getId())).get("id").asLong();
    }

    @Test
    void creerUneFichePourUnePlantationPerennneEtDeriverLaCulture() throws Exception {
        JsonNode fiche = creerPlantation();
        assertThat(fiche.get("datePlantation").asText()).isEqualTo("2010-01-01");
        assertThat(fiche.get("cultureNom").asText()).isEqualTo("Olivier");
        assertThat(fiche.get("campagneId").asLong()).isEqualTo(campagneId);
        assertThat(fiche.has("version")).isFalse();
        assertThat(fiche.has("parcelles")).isFalse();
    }

    @Test
    void seulsLesAgriculteursAffectesEcriventLesFiches() throws Exception {
        long id = creerPlantation().get("id").asLong();
        for (String role : new String[]{"ADMIN", "AGRONOME"}) {
            api.perform(get("/api/plantations/" + id).header("Authorization", "Bearer " + role))
                    .andExpect(status().isOk());
            api.perform(post("/api/plantations").header("Authorization", "Bearer " + role)
                    .contentType("application/json").content(demandePlantation()))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/plantations/" + id).header("Authorization", "Bearer " + role)
                    .contentType("application/json").content(demandePlantation()))
                    .andExpect(status().isForbidden());
        }
        api.perform(get("/api/plantations/pagination")).andExpect(status().isUnauthorized());
        api.perform(put("/api/plantations/" + id).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantation().replace("2.0", "3.0")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.superficie").value(3.0));
    }

    @Test
    void refuserParcelleNonAffecteeEtCampagneSansCetteParcelle() throws Exception {
        Parcelle privee = creerParcellePrivee();
        api.perform(post("/api/plantations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantationPour(privee.getId(), campagneId, varieteId)))
                .andExpect(status().isForbidden());
        long autreCampagne = creerAvecParcelles("Autre", List.of(privee.getId())).get("id").asLong();
        api.perform(post("/api/plantations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantationPour(parcelle.getId(), autreCampagne, varieteId)))
                .andExpect(status().isBadRequest());
        assertThat(plantations.count()).isZero();
    }

    @Test
    void datesSuperficiesQuantitesEtReferencesValidees() throws Exception {
        for (String contenu : new String[]{
                demandePlantation().replace("2.0", "0.0"),
                demandePlantation().replace("2.0", "6.0"),
                demandePlantation().replace("2010-01-01", "2028-01-01"),
                demandePlantation().replace("2027-10-15", "2028-10-15"),
                demandePlantation().replace("2027-10-15", "2009-10-15"),
                demandePlantation().replace("EN_COURS", "INCONNU"),
                "{}"}) {
            api.perform(post("/api/plantations").header("Authorization", "Bearer AGRICULTEUR")
                    .contentType("application/json").content(contenu)).andExpect(status().isBadRequest());
        }
        api.perform(post("/api/plantations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantationPour(parcelle.getId(), campagneId, 999999L)))
                .andExpect(status().isNotFound());
        assertThat(plantations.count()).isZero();
    }

    @Test
    void clotureBloqueCreationEtModificationSansMasquerLaLecture() throws Exception {
        long id = creerPlantation().get("id").asLong();
        statut(campagneId, "EN_COURS", 200);
        statut(campagneId, "CLOTUREE", 200);
        api.perform(post("/api/plantations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantation())).andExpect(status().isConflict());
        api.perform(put("/api/plantations/" + id).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantation())).andExpect(status().isConflict());
        api.perform(get("/api/plantations/" + id).header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.campagneCloturee").value(true));
    }

    @Test
    void retraitAffectationRetireDetailModificationEtTotaux() throws Exception {
        long id = creerPlantation().get("id").asLong();
        affectations.deleteAll();
        for (String role : new String[]{"AGRICULTEUR", "AGRONOME"}) {
            api.perform(get("/api/plantations/" + id).header("Authorization", "Bearer " + role))
                    .andExpect(status().isForbidden());
            api.perform(get("/api/plantations/pagination").header("Authorization", "Bearer " + role))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            api.perform(get("/api/plantations/pagination").param("parcelleId", parcelle.getId().toString())
                    .header("Authorization", "Bearer " + role)).andExpect(status().isForbidden());
        }
        api.perform(put("/api/plantations/" + id).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantation())).andExpect(status().isForbidden());
        api.perform(get("/api/plantations/pagination").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filtrerAvantPaginationSansRevelerLesPlantationsPrivees() throws Exception {
        creerPlantation();
        long dernier = creerPlantation().get("id").asLong();
        Parcelle privee = creerParcellePrivee();
        long campagnePrivee = creerAvecParcelles("Privee", List.of(privee.getId())).get("id").asLong();
        plantations.saveAndFlush(com.example.backend.entity.Plantation.builder()
                .parcelle(privee).campagne(campagnes.findById(campagnePrivee).orElseThrow())
                .variete(varietes.findById(varieteId).orElseThrow()).superficie(1.0)
                .datePlantation(java.time.LocalDate.of(2027, 1, 1))
                .statut(com.example.backend.enums.StatutPlantation.PLANIFIEE).build());
        api.perform(get("/api/plantations/pagination").param("taille", "1")
                .header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.contenu[0].id").value(dernier));
        api.perform(get("/api/plantations/pagination").param("statut", "PLANIFIEE")
                .header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        api.perform(get("/api/plantations/pagination").param("campagneId", Long.toString(campagneId))
                .header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void protegerLeCatalogueLesDatesLaSurfaceEtLesRattachements() throws Exception {
        long id = creerPlantation().get("id").asLong();
        var autreCulture = cultures.saveAndFlush(com.example.backend.entity.Culture.builder()
                .nom("Tomate").nomNormalise("tomate").build());
        api.perform(put("/api/varietes/" + varieteId).header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "nom", "Chemlali", "cultureId", autreCulture.getId())))).andExpect(status().isConflict());
        api.perform(put("/api/varietes/" + varieteId).header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "nom", "Chemlali renomme", "cultureId", varietes.findById(varieteId).orElseThrow().getCulture().getId()))))
                .andExpect(status().isOk());
        Parcelle privee = creerParcellePrivee();
        api.perform(put("/api/campagnes/" + campagneId).header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "nom", "Suivi 2027", "dateDebut", "2027-01-01", "dateFin", "2027-12-31",
                        "parcelleIds", List.of(privee.getId()))))).andExpect(status().isConflict());
        api.perform(put("/api/campagnes/" + campagneId).header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content(demande("Suivi 2027", "2027-01-01", "2027-09-01")))
                .andExpect(status().isConflict());
        api.perform(put("/api/parcelles/" + parcelle.getId()).header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "nom", "Parcelle", "superficie", 1.0, "exploitationId", parcelle.getExploitation().getId()))))
                .andExpect(status().isConflict());
        long autreCampagne = creerAvecParcelles("Autre", List.of(parcelle.getId())).get("id").asLong();
        api.perform(put("/api/plantations/" + id).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantationPour(parcelle.getId(), autreCampagne, varieteId)))
                .andExpect(status().isConflict());
    }

    private JsonNode creerPlantation() throws Exception {
        return json.readTree(api.perform(post("/api/plantations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantation())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String demandePlantation() throws Exception {
        return demandePlantationPour(parcelle.getId(), campagneId, varieteId);
    }

    private String demandePlantationPour(long parcelleId, long campagneId, long varieteId) throws Exception {
        return json.writeValueAsString(Map.of("parcelleId", parcelleId, "campagneId", campagneId,
                "varieteId", varieteId, "datePlantation", "2010-01-01", "superficie", 2.0,
                "dateRecoltePrevue", "2027-10-15", "statut", "EN_COURS"));
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
