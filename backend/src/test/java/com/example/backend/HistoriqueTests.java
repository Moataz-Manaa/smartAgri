package com.example.backend;

import com.example.backend.entity.Campagne;
import com.example.backend.entity.Exploitation;
import com.example.backend.entity.Parcelle;
import com.example.backend.entity.Utilisateur;
import com.example.backend.entity.Affectation;
import com.example.backend.enums.Role;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class HistoriqueTests {
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

    @Autowired com.example.backend.repository.RecolteRepository recoltes;

    @BeforeEach
    void preparer() throws Exception {
        recoltes.deleteAll();
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
    void regroupeLesCinqTypesEtPagineApresLeTriGlobal() throws Exception {
        ajouterCinqActivites();
        api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId)
                .param("taille", "2").header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.contenu", hasSize(2)))
                .andExpect(jsonPath("$.contenu[0].type").value("RECOLTE"))
                .andExpect(jsonPath("$.contenu[0].quantite").value(2))
                .andExpect(jsonPath("$.contenu[0].unite").value("TONNE"))
                .andExpect(jsonPath("$.contenu[0].auteurNom").value("AGRICULTEUR"))
                .andExpect(jsonPath("$.contenu[1].type").value("FERTILISATION"));
        api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId)
                .param("taille", "2").param("page", "1").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].type").value("TRAITEMENT"))
                .andExpect(jsonPath("$.contenu[1].type").value("IRRIGATION"));
        api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId)
                .param("taille", "2").param("page", "2").header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].type").value("OBSERVATION"))
                .andExpect(jsonPath("$.contenu[0].description").value("Feuilles observees"))
                .andExpect(jsonPath("$.contenu[0].quantite").value(nullValue()));
    }

    @Test
    void filtreLesDatesInclusivesEtLeTypeAvantLeComptage() throws Exception {
        ajouterCinqActivites();
        api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId)
                .param("dateDebut", "2020-06-03").param("dateFin", "2020-06-03")
                .param("taille", "1").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId)
                .param("type", "TRAITEMENT").param("dateDebut", "2020-06-03")
                .param("dateFin", "2020-06-03").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].nomProduit").value("Produit test"));
    }

    @Test
    void neMelangePasDeuxPlantationsEtRenvoieUnePageVide() throws Exception {
        ajouterCinqActivites();
        long autre = creerPlantation().get("id").asLong();
        api.perform(get("/api/historique/pagination").param("plantationId", "" + autre)
                .header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.contenu", hasSize(0)));
        api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId)
                .param("dateDebut", "2021-01-01").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void refuseLesFiltresEtLaPaginationInvalides() throws Exception {
        for (Map<String, String> parametres : List.of(Map.of("dateDebut", "2020-10-01", "dateFin", "2020-01-01"),
                Map.of("dateDebut", "invalide"), Map.of("type", "INCONNU"), Map.of("page", "-1"),
                Map.of("taille", "0"), Map.of("taille", "101"))) {
            var requete = get("/api/historique/pagination").param("plantationId", "" + plantationId)
                    .header("Authorization", "Bearer ADMIN");
            parametres.forEach(requete::param);
            api.perform(requete).andExpect(status().isBadRequest());
        }
    }

    @Test
    void controleAffectationsAvantDeRetournerLesActivitesEtLeurNombre() throws Exception {
        ajouterCinqActivites();
        affectations.deleteAll();
        for (String role : List.of("AGRICULTEUR", "AGRONOME")) {
            api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId)
                    .header("Authorization", "Bearer " + role)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.totalElements").doesNotExist());
        }
        api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId))
                .andExpect(status().isUnauthorized());
        api.perform(get("/api/historique/pagination").param("plantationId", "9223372036854775807")
                .header("Authorization", "Bearer ADMIN")).andExpect(status().isNotFound());
    }

    @Test
    void refleteLesCorrectionsSansDuplicationEtResteLisibleApresCloture() throws Exception {
        ajouterCinqActivites();
        var observation = observations.findAll().getFirst();
        api.perform(put("/api/observations/" + observation.getId()).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "plantationId", plantationId, "dateObservation", "2020-06-05", "description", "Corrigee"))))
                .andExpect(status().isOk());
        api.perform(put("/api/campagnes/" + campagneId + "/statut").header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content("{\"statut\":\"EN_COURS\"}"))
                .andExpect(status().isOk());
        api.perform(put("/api/campagnes/" + campagneId + "/statut").header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content("{\"statut\":\"CLOTUREE\"}"))
                .andExpect(status().isOk());
        api.perform(get("/api/historique/pagination").param("plantationId", "" + plantationId)
                .header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.contenu[0].description").value("Corrigee"));
    }

    private void ajouterCinqActivites() throws Exception {
        ajouter("observations", Map.of("plantationId", plantationId, "dateObservation", "2020-06-01", "description", "Feuilles observees"));
        ajouter("irrigations", Map.of("plantationId", plantationId, "dateIrrigation", "2020-06-02", "volume", 100, "unite", "LITRE"));
        ajouter("fertilisations", Map.of("plantationId", plantationId, "dateFertilisation", "2020-06-03", "quantite", 5,
                "unite", "KILOGRAMME", "nomProduit", "Engrais test"));
        ajouter("traitements", Map.of("plantationId", plantationId, "dateTraitement", "2020-06-03", "quantite", 2,
                "unite", "LITRE", "nomProduit", "Produit test", "cible", "Cible test"));
        ajouter("recoltes", Map.of("plantationId", plantationId, "dateRecolte", "2020-06-04", "quantite", 2, "unite", "TONNE"));
    }

    private void ajouter(String module, Map<String, Object> demande) throws Exception {
        api.perform(post("/api/" + module).header("Authorization", "Bearer AGRICULTEUR")
                .contentType("application/json").content(json.writeValueAsString(demande)))
                .andExpect(status().isCreated());
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

    private JsonNode creerAvecParcelles(String nom, List<Long> ids) throws Exception {
        return json.readTree(api.perform(post("/api/campagnes").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "nom", nom, "dateDebut", "2020-01-01", "dateFin", "2020-12-31", "parcelleIds", ids))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }


}
