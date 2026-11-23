package com.example.backend;

import com.example.backend.entity.*;
import com.example.backend.dto.meteo.*;
import com.example.backend.service.OpenWeatherClient;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import com.example.backend.enums.Role;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Vraie API et vrais repositories ; seul le fournisseur d'identité est simulé.
@SpringBootTest
@AutoConfigureMockMvc
class MeteoAccesTests {
    @MockitoBean OpenWeatherClient fournisseur;
    @Autowired MockMvc api;
    @Autowired AffectationRepository affectations;
    @Autowired CampagneRepository campagnes;
    @Autowired ExploitationRepository exploitations;
    @Autowired ParcelleRepository parcelles;
    @Autowired UtilisateurRepository utilisateurs;
    @MockitoBean TokenService tokens;
    @MockitoBean CompteKeycloakService comptes;

    Exploitation sfax;
    Exploitation nabeul;
    Parcelle p1;
    Parcelle p2;
    Parcelle p3;
    Utilisateur a;
    Utilisateur b;
    Utilisateur agronome;
    Affectation affectationA1;

    @Autowired com.example.backend.repository.PlantationRepository plantations;

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
        affectations.deleteAll();
        parcelles.deleteAll();
        exploitations.deleteAll();
        utilisateurs.deleteAll();
        identite("admin", Role.ADMIN);
        identite("a", Role.AGRICULTEUR);
        identite("b", Role.AGRICULTEUR);
        identite("agronome", Role.AGRONOME);
        identite("sans-affectation", Role.AGRICULTEUR);
        sfax = exploitation("EXP-TEST-1");
        nabeul = exploitation("EXP-TEST-2");
        p1 = parcelle("PAR-TEST-1", sfax);
        p2 = parcelle("PAR-TEST-2", sfax);
        p3 = parcelle("PAR-TEST-3", nabeul);
        a = utilisateur("a");
        b = utilisateur("b");
        agronome = utilisateur("agronome");
        affectationA1 = affecter(a, p1);
        affecter(a, p2);
        affecter(b, p1);
        affecter(b, p3);
        affecter(agronome, p1);
        affecter(agronome, p2);
        affecter(agronome, p3);
        sfax.setLatitude(34.0 + sfax.getId() / 100000.0);
        sfax.setLongitude(10.76);
        exploitations.saveAndFlush(sfax);
        when(fournisseur.consulter(anyDouble(), anyDouble()))
                .thenAnswer(invocation -> donneesMeteo());
    }

    private DonneesMeteoResponse donneesMeteo() {
        Instant maintenant = Instant.now();
        return DonneesMeteoResponse.builder().recupereesLe(maintenant).decalageUtcSecondes(3600)
                .conditions(ConditionsMeteoResponse.builder().dateDonnees(maintenant).temperatureCelsius(22.0).build())
                .previsions(java.util.List.of(PrevisionMeteoResponse.builder()
                        .datePrevision(maintenant.plusSeconds(10800)).build())).build();
    }

    @Test void consulterAvecLesTroisRolesEtPartagerLeCache() throws Exception {
        for (String role : new String[]{"admin", "a", "agronome"}) {
            api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer " + role))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("DISPONIBLE"))
                    .andExpect(jsonPath("$.source").value("OpenWeather"))
                    .andExpect(jsonPath("$.parcelleId").value(p1.getId()))
                    .andExpect(jsonPath("$.donnees.conditions.temperatureCelsius").value(22.0))
                    .andExpect(jsonPath("$.apiKey").doesNotExist());
        }
        api.perform(get("/api/parcelles/" + p2.getId() + "/meteo").header("Authorization", "Bearer a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcelleId").value(p2.getId()));
        verify(fournisseur, times(1))
                .consulter(anyDouble(), anyDouble());
    }

    @Test void refuserLesParcellesNonAffecteesAvantLeFournisseur() throws Exception {
        api.perform(get("/api/parcelles/" + p3.getId() + "/meteo").header("Authorization", "Bearer a"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer sans-affectation"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo")).andExpect(status().isUnauthorized());
        api.perform(get("/api/parcelles/9223372036854775807/meteo").header("Authorization", "Bearer admin"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(fournisseur);
    }

    @Test void retraitIndividuelBloqueAussiLesDonneesEnCache() throws Exception {
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer a"))
                .andExpect(status().isOk());
        affectations.deleteById(affectationA1.getId());
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer a"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer b"))
                .andExpect(status().isOk());
        api.perform(get("/api/parcelles/" + p2.getId() + "/meteo").header("Authorization", "Bearer a"))
                .andExpect(status().isOk());
    }

    @Test void informerQuandLaLocalisationEstAbsenteOuPartielle() throws Exception {
        sfax.setLatitude(null);
        sfax.setLongitude(null);
        exploitations.saveAndFlush(sfax);
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("LOCALISATION_MANQUANTE"))
                .andExpect(jsonPath("$.donnees").value(nullValue()));
        sfax.setLongitude(10.76);
        exploitations.saveAndFlush(sfax);
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("LOCALISATION_MANQUANTE"));
        verifyNoInteractions(fournisseur);
    }

    @Test void accepterZeroEtRefuserUneAncienneLocalisationHorsLimites() throws Exception {
        sfax.setLatitude(0.0);
        sfax.setLongitude(0.0);
        exploitations.saveAndFlush(sfax);
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("DISPONIBLE"))
                .andExpect(jsonPath("$.latitude").value(0));
        sfax.setLatitude(91.0);
        exploitations.saveAndFlush(sfax);
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("LOCALISATION_INVALIDE"));
        verify(fournisseur).consulter(0, 0);
    }

    @Test void exposerUnePanneSansInventerDesMesures() throws Exception {
        when(fournisseur.consulter(anyDouble(), anyDouble()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Quota temporairement atteint"));
        api.perform(get("/api/parcelles/" + p1.getId() + "/meteo").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("INDISPONIBLE"))
                .andExpect(jsonPath("$.donnees").value(nullValue()));
    }


    private void identite(String id, Role role) throws Exception {
        when(tokens.verifier(id)).thenReturn(new JWTClaimsSet.Builder().subject(id).build());
        UserRepresentation compte = new UserRepresentation();
        compte.setId(id);
        compte.setEnabled(true);
        when(comptes.consulter(id)).thenReturn(compte);
        when(comptes.consulterRole(id)).thenReturn(role);
    }

    private Exploitation exploitation(String code) {
        return exploitations.saveAndFlush(Exploitation.builder().code(code).nom(code).superficie(20.0).build());
    }

    private Parcelle parcelle(String code, Exploitation exploitation) {
        return parcelles.saveAndFlush(Parcelle.builder().code(code).nom(code).superficie(5.0).exploitation(exploitation).build());
    }

    private Utilisateur utilisateur(String id) {
        return utilisateurs.saveAndFlush(Utilisateur.builder().keycloakId(id).nom(id).prenom("Test").email(id + "@example.test").build());
    }

    private Affectation affecter(Utilisateur utilisateur, Parcelle parcelle) {
        return affectations.saveAndFlush(Affectation.builder().utilisateur(utilisateur).parcelle(parcelle).build());
    }


}
