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
class FertilisationTests {
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
    private long plantationId;

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
        campagneId = creerAvecParcelles("Suivi 2020", List.of(parcelle.getId())).get("id").asLong();
        plantationId = creerPlantation().get("id").asLong();
    }

    @Test
    void auteurEstDetermineParLeBackendEtIgnoreLeCorpsEnvoye() throws Exception {
        JsonNode fertilisation = creerFertilisation("2020-06-01", "  Operation du matin  ");
        assertThat(fertilisation.get("commentaire").asText()).isEqualTo("Operation du matin");
        assertThat(fertilisation.get("auteurNom").asText()).isEqualTo("AGRICULTEUR");
        assertThat(fertilisation.has("email")).isFalse();
        assertThat(fertilisation.has("version")).isFalse();
    }

    @Test
    void seulsLesAgriculteursAffectesPeuventEcrire() throws Exception {
        long id = creerFertilisation("2020-06-01", "Fertilisation").get("id").asLong();
        for (String role : new String[]{"ADMIN", "AGRONOME"}) {
            api.perform(get("/api/fertilisations/" + id).header("Authorization", "Bearer " + role))
                    .andExpect(status().isOk());
            api.perform(post("/api/fertilisations").header("Authorization", "Bearer " + role)
                    .contentType("application/json").content(demandeFertilisation("2020-06-01", "Interdit")))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/fertilisations/" + id).header("Authorization", "Bearer " + role)
                    .contentType("application/json").content(demandeFertilisation("2020-06-01", "Interdit")))
                    .andExpect(status().isForbidden());
        }
        api.perform(get("/api/fertilisations/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void modifierLeTexteConserveLauteurInitial() throws Exception {
        long id = creerFertilisation("2020-06-01", "Initiale").get("id").asLong();
        String keycloakId = "autre-agriculteur";
        Utilisateur autre = utilisateurs.saveAndFlush(Utilisateur.builder().keycloakId(keycloakId)
                .nom("Autre").prenom("Personne").email("autre@example.test").build());
        affectations.saveAndFlush(Affectation.builder().parcelle(parcelle).utilisateur(autre).build());
        when(tokens.verifier("AUTRE")).thenReturn(new JWTClaimsSet.Builder().subject(keycloakId).build());
        UserRepresentation compte = new UserRepresentation();
        compte.setEnabled(true);
        when(comptes.consulter(keycloakId)).thenReturn(compte);
        when(comptes.consulterRole(keycloakId)).thenReturn(Role.AGRICULTEUR);
        api.perform(put("/api/fertilisations/" + id).header("Authorization", "Bearer AUTRE")
                .contentType("application/json").content(demandeFertilisation("2020-06-02", "Corrigee")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commentaire").value("Corrigee"))
                .andExpect(jsonPath("$.auteurNom").value("AGRICULTEUR"));
    }

    @Test
    void validerDatesDescriptionEtReferences() throws Exception {
        for (String date : new String[]{"2019-12-31", "2021-01-01", "2999-01-01", "invalide"}) {
            api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                    .contentType("application/json").content(demandeFertilisation(date, "Invalide")))
                    .andExpect(status().isBadRequest());
        }
        for (String commentaire : new String[]{"  ", "a".repeat(2001)}) {
            api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                    .contentType("application/json").content(demandeFertilisation("2020-06-01", "Test").replace("Produit test", commentaire)))
                    .andExpect(status().isBadRequest());
        }
        api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "plantationId", 999999L, "dateFertilisation", "2020-06-01", "commentaire", "Absente", "nomProduit", "Produit test", "quantite", 2, "unite", "KILOGRAMME"))))
                .andExpect(status().isNotFound());
        assertThat(fertilisations.count()).isZero();
    }

    @Test
    void clotureBloqueLesEcrituresMaisGardeLaConsultation() throws Exception {
        long id = creerFertilisation("2020-06-01", "Initiale").get("id").asLong();
        statut(campagneId, "EN_COURS", 200);
        statut(campagneId, "CLOTUREE", 200);
        api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandeFertilisation("2020-06-02", "Interdit")))
                .andExpect(status().isConflict());
        api.perform(put("/api/fertilisations/" + id).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandeFertilisation("2020-06-02", "Interdit")))
                .andExpect(status().isConflict());
        api.perform(get("/api/fertilisations/" + id).header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.campagneCloturee").value(true));
    }

    @Test
    void retraitAffectationBloqueDetailListeTotauxEtModification() throws Exception {
        long id = creerFertilisation("2020-06-01", "Initiale").get("id").asLong();
        affectations.deleteAll();
        for (String role : new String[]{"AGRICULTEUR", "AGRONOME"}) {
            api.perform(get("/api/fertilisations/" + id).header("Authorization", "Bearer " + role))
                    .andExpect(status().isForbidden());
            api.perform(get("/api/fertilisations/pagination").param("plantationId", Long.toString(plantationId))
                    .header("Authorization", "Bearer " + role)).andExpect(status().isForbidden());
        }
        api.perform(put("/api/fertilisations/" + id).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandeFertilisation("2020-06-02", "Interdit")))
                .andExpect(status().isForbidden());
        api.perform(get("/api/fertilisations/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void paginationTrieParDatePuisIdentifiantEtResteSurLaPlantation() throws Exception {
        creerFertilisation("2020-01-01", "Ancienne");
        creerFertilisation("2020-06-01", "Premiere");
        long derniere = creerFertilisation("2020-06-01", "Derniere").get("id").asLong();
        long autre = creerPlantation().get("id").asLong();
        api.perform(get("/api/fertilisations/pagination").param("plantationId", Long.toString(plantationId))
                .param("taille", "1").header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.contenu[0].id").value(derniere));
        api.perform(get("/api/fertilisations/pagination").param("plantationId", Long.toString(autre))
                .header("Authorization", "Bearer AGRICULTEUR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void empecherLesModificationsDeParentsQuiInvalidentUneFertilisation() throws Exception {
        creerFertilisation("2020-06-01", "Historique");
        api.perform(put("/api/plantations/" + plantationId).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandePlantation().replace("2010-01-01", "2020-07-01")))
                .andExpect(status().isConflict());
        api.perform(put("/api/campagnes/" + campagneId).header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content(demande("Suivi 2020", "2020-07-01", "2020-12-31")))
                .andExpect(status().isConflict());
        long id = fertilisations.findAll().getFirst().getId();
        long autre = creerPlantation().get("id").asLong();
        api.perform(put("/api/fertilisations/" + id).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "plantationId", autre, "dateFertilisation", "2020-06-01", "commentaire", "Deplacement", "nomProduit", "Produit test", "quantite", 2, "unite", "KILOGRAMME"))))
                .andExpect(status().isConflict());
    }

    @Test
    void validerQuantitesUnitesEtChampsPropres() throws Exception {
        for (String quantite : new String[]{"0", "-1", "0.0001", "1000000000000", "null"}) {
            api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                    .contentType("application/json").content(demandeFertilisation("2020-06-01", "Test")
                            .replace("\"quantite\":2", "\"quantite\":" + quantite)))
                    .andExpect(status().isBadRequest());
        }
        api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandeFertilisation("2020-06-01", "a".repeat(256))))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandeFertilisation("2020-06-01", "Test").replace("KILOGRAMME", "INCONNUE")))
                .andExpect(status().isBadRequest());
        assertThat(fertilisations.count()).isZero();
        for (String unite : new String[]{"GRAMME", "KILOGRAMME", "MILLILITRE", "LITRE"}) {
            api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                    .contentType("application/json").content(demandeFertilisation("2020-06-01", "Test").replace("KILOGRAMME", unite)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.unite").value(unite));
        }
    }

    private String demandeFertilisation(String date, String commentaire) throws Exception {
        return json.writeValueAsString(Map.of("plantationId", plantationId, "dateFertilisation", date,
                "commentaire", commentaire, "nomProduit", "Produit test", "quantite", 2, "unite", "KILOGRAMME",
                "auteurId", 999999L, "auteurNom", "Usurpation", "typeEngrais", "Type test"));
    }

    private JsonNode creerFertilisation(String date, String commentaire) throws Exception {
        return json.readTree(api.perform(post("/api/fertilisations").header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(demandeFertilisation(date, commentaire)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
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
                "dateRecoltePrevue", "2020-10-15", "statut", "EN_COURS"));
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
                                "nom", nom, "dateDebut", "2020-01-01", "dateFin", "2020-12-31", "parcelleIds", ids))))
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
