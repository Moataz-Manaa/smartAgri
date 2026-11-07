package com.example.backend;

import com.example.backend.entity.Culture;
import com.example.backend.entity.Variete;
import com.example.backend.enums.Role;
import com.example.backend.repository.CultureRepository;
import com.example.backend.repository.VarieteRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CatalogueAgricoleTests {
    @Autowired MockMvc api;
    @Autowired CultureRepository cultures;
    @Autowired VarieteRepository varietes;
    @MockitoBean TokenService tokens;
    @MockitoBean CompteKeycloakService comptes;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired com.example.backend.repository.PlantationRepository plantations;

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
        varietes.deleteAll();
        cultures.deleteAll();
        for (Role role : Role.values()) {
            String id = "catalogue-" + role.name();
            when(tokens.verifier(role.name())).thenReturn(new JWTClaimsSet.Builder().subject(id).build());
            UserRepresentation compte = new UserRepresentation();
            compte.setEnabled(true);
            when(comptes.consulter(id)).thenReturn(compte);
            when(comptes.consulterRole(id)).thenReturn(role);
        }
    }

    @Test
    void administrateurCreeModifieEtConsulteUneCulture() throws Exception {
        JsonNode culture = creerCulture(" Olivier ");
        long id = culture.get("id").asLong();
        assertThat(culture.get("nom").asText()).isEqualTo("Olivier");
        assertThat(culture.has("nomNormalise")).isFalse();
        api.perform(put("/api/cultures/" + id).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(
                                Map.of("nom", "Olivier modifié", "type", "Arboriculture", "description", "Description"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("Arboriculture"));
        api.perform(get("/api/cultures/" + id).header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Olivier modifié"));
    }

    @Test
    void agriculteurEtAgronomeConsultentSansModifierLeCatalogue() throws Exception {
        JsonNode culture = creerCulture("Olivier");
        JsonNode variete = creerVariete("Chemlali", culture.get("id").asLong());
        for (String role : new String[]{"AGRICULTEUR", "AGRONOME"}) {
            for (String chemin : new String[]{"/api/cultures", "/api/varietes", "/api/cultures/" + culture.get("id"),
                    "/api/varietes/" + variete.get("id"), "/api/cultures/pagination", "/api/varietes/pagination"}) {
                api.perform(get(chemin).header("Authorization", "Bearer " + role)).andExpect(status().isOk());
            }
            api.perform(post("/api/cultures").header("Authorization", "Bearer " + role)
                            .contentType("application/json").content("{\"nom\":\"Tomate\"}"))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/cultures/" + culture.get("id")).header("Authorization", "Bearer " + role)
                            .contentType("application/json").content("{\"nom\":\"Interdit\"}"))
                    .andExpect(status().isForbidden());
            api.perform(post("/api/varietes").header("Authorization", "Bearer " + role)
                            .contentType("application/json").content(json.writeValueAsString(
                                    Map.of("nom", "Interdit", "cultureId", culture.get("id").asLong()))))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/varietes/" + variete.get("id")).header("Authorization", "Bearer " + role)
                            .contentType("application/json").content(json.writeValueAsString(
                                    Map.of("nom", "Interdit", "cultureId", culture.get("id").asLong()))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void cultureRefuseDoublonsEtConflitsDeRenommage() throws Exception {
        creerCulture("Olivier");
        api.perform(post("/api/cultures").header("Authorization", "Bearer ADMIN").contentType("application/json")
                        .content("{\"nom\":\"  OLIVIER  \"}"))
                .andExpect(status().isConflict());
        JsonNode tomate = creerCulture("Tomate");
        api.perform(put("/api/cultures/" + tomate.get("id")).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content("{\"nom\":\"olivier\"}"))
                .andExpect(status().isConflict());
        assertThat(cultures.count()).isEqualTo(2);
        assertThatThrownBy(() -> cultures.saveAndFlush(Culture.builder()
                .nom("Autre casse").nomNormalise("olivier").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void varieteUniqueDansUneCultureMaisPossibleDansUneAutre() throws Exception {
        long premierId = creerCulture("Olivier").get("id").asLong();
        long secondId = creerCulture("Tomate").get("id").asLong();
        creerVariete("Commune", premierId);
        api.perform(post("/api/varietes").header("Authorization", "Bearer ADMIN").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("nom", "  COMMUNE  ", "cultureId", premierId))))
                .andExpect(status().isConflict());
        JsonNode autre = creerVariete("Commune", secondId);
        api.perform(put("/api/varietes/" + autre.get("id")).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(
                                Map.of("nom", "Commune", "cultureId", premierId))))
                .andExpect(status().isConflict());
        assertThat(varietes.count()).isEqualTo(2);
        assertThatThrownBy(() -> varietes.saveAndFlush(Variete.builder().nom("Autre").nomNormalise("commune")
                .culture(cultures.findById(premierId).orElseThrow()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void modifierUneVarieteSansChangerSonIdentite() throws Exception {
        long cultureId = creerCulture("Olivier").get("id").asLong();
        JsonNode variete = creerVariete("Chemlali", cultureId);
        api.perform(put("/api/varietes/" + variete.get("id")).header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(
                                Map.of("nom", " Chemlali Sfax ", "description", "Description", "cultureId", cultureId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(variete.get("id").asLong()))
                .andExpect(jsonPath("$.nom").value("Chemlali Sfax")).andExpect(jsonPath("$.cultureNom").value("Olivier"));
        api.perform(get("/api/varietes/" + variete.get("id")).header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Description"))
                .andExpect(jsonPath("$.nomNormalise").doesNotExist());
    }

    @Test
    void varietesFiltreesEtPagineesAvantLeComptage() throws Exception {
        long olivier = creerCulture("Olivier").get("id").asLong();
        long tomate = creerCulture("Tomate").get("id").asLong();
        JsonNode premiere = creerVariete("Chemlali", olivier);
        JsonNode seconde = creerVariete("Chetoui", olivier);
        creerVariete("Roma", tomate);
        api.perform(get("/api/varietes/pagination").param("cultureId", Long.toString(olivier))
                        .param("taille", "1").header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.contenu[0].id").value(seconde.get("id").asLong()));
        api.perform(get("/api/varietes/pagination").param("cultureId", Long.toString(olivier))
                        .param("taille", "1").param("page", "1").header("Authorization", "Bearer AGRICULTEUR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].id").value(premiere.get("id").asLong()));
        api.perform(get("/api/varietes").param("cultureId", Long.toString(tomate)).header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].nom").value("Roma"));
        api.perform(get("/api/cultures/pagination").param("taille", "1").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void refuserDonneesInvalidesEtRattachementAbsent() throws Exception {
        for (String nom : new String[]{"", "   ", "x".repeat(101)}) {
            api.perform(post("/api/cultures").header("Authorization", "Bearer ADMIN").contentType("application/json")
                            .content(json.writeValueAsString(Map.of("nom", nom))))
                    .andExpect(status().isBadRequest());
        }
        api.perform(post("/api/varietes").header("Authorization", "Bearer ADMIN").contentType("application/json")
                        .content("{\"nom\":\"Chemlali\"}")).andExpect(status().isBadRequest());
        api.perform(post("/api/varietes").header("Authorization", "Bearer ADMIN").contentType("application/json")
                        .content("{\"nom\":\"Chemlali\",\"cultureId\":999999}")).andExpect(status().isNotFound());
        api.perform(get("/api/varietes/pagination").param("cultureId", "999999")
                        .header("Authorization", "Bearer ADMIN")).andExpect(status().isNotFound());
        for (String chemin : new String[]{"/api/cultures/pagination", "/api/varietes/pagination"}) {
            api.perform(get(chemin).param("taille", "101").header("Authorization", "Bearer ADMIN"))
                    .andExpect(status().isBadRequest());
            api.perform(get(chemin).param("page", "-1").header("Authorization", "Bearer ADMIN"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(cultures.count()).isZero();
        assertThat(varietes.count()).isZero();
    }

    @Test
    void refuserAccesNonAuthentifieEtRetournerListesVides() throws Exception {
        for (String chemin : new String[]{"/api/cultures", "/api/varietes", "/api/cultures/pagination", "/api/varietes/pagination"}) {
            api.perform(get(chemin)).andExpect(status().isUnauthorized());
        }
        api.perform(get("/api/cultures/pagination").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.contenu", hasSize(0)));
        api.perform(get("/api/varietes/999999").header("Authorization", "Bearer AGRONOME"))
                .andExpect(status().isNotFound());
    }

    private JsonNode creerCulture(String nom) throws Exception {
        return json.readTree(api.perform(post("/api/cultures").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(Map.of("nom", nom))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode creerVariete(String nom, long cultureId) throws Exception {
        return json.readTree(api.perform(post("/api/varietes").header("Authorization", "Bearer ADMIN")
                        .contentType("application/json").content(json.writeValueAsString(Map.of("nom", nom, "cultureId", cultureId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
}
