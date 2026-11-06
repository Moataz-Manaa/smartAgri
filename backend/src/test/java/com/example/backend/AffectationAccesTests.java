package com.example.backend;

import com.example.backend.entity.*;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Vraie API et vrais repositories ; seul le fournisseur d'identité est simulé.
@SpringBootTest
@AutoConfigureMockMvc
class AffectationAccesTests {
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

    @BeforeEach
    void preparer() throws Exception {
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
    }

    @Test
    void filtrerParcellesEtRefuserAccesDirect() throws Exception {
        api.perform(get("/api/parcelles").header("Authorization", "Bearer a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(p1.getId().intValue(), p2.getId().intValue())));
        api.perform(get("/api/parcelles/" + p1.getId()).header("Authorization", "Bearer a"))
                .andExpect(status().isOk());
        api.perform(get("/api/parcelles/" + p3.getId()).header("Authorization", "Bearer a"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/exploitations/" + nabeul.getId()).header("Authorization", "Bearer a"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exploitationAccessibleNeDonnePasToutesSesParcelles() throws Exception {
        api.perform(get("/api/parcelles/exploitation/" + sfax.getId()).header("Authorization", "Bearer b"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(p1.getId()));
        api.perform(get("/api/parcelles/" + p2.getId()).header("Authorization", "Bearer b"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/exploitations").header("Authorization", "Bearer a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void agronomeEtAdministrateurConsultentLeursDonnees() throws Exception {
        for (String compte : new String[]{"agronome", "admin"}) {
            api.perform(get("/api/parcelles").header("Authorization", "Bearer " + compte))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
            api.perform(get("/api/exploitations").header("Authorization", "Bearer " + compte))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        }
    }

    @Test
    void utilisateurSansAffectationVoitDesListesVides() throws Exception {
        api.perform(get("/api/parcelles").header("Authorization", "Bearer sans-affectation"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        api.perform(get("/api/exploitations").header("Authorization", "Bearer sans-affectation"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void administrateurAjoutePlusieursUtilisateursEtInterditDoublons() throws Exception {
        api.perform(post("/api/affectations").header("Authorization", "Bearer admin")
                        .contentType("application/json").content(demande(a.getId(), p3.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("AGRICULTEUR"));
        api.perform(post("/api/affectations").header("Authorization", "Bearer admin")
                        .contentType("application/json").content(demande(a.getId(), p3.getId())))
                .andExpect(status().isConflict());
        api.perform(get("/api/affectations").param("parcelleId", p3.getId().toString())
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        assertThatThrownBy(() -> affecter(a, p3)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void retraitIndividuelRetireAccesImmediatement() throws Exception {
        api.perform(delete("/api/affectations/" + affectationA1.getId())
                        .header("Authorization", "Bearer admin")).andExpect(status().isNoContent());
        api.perform(get("/api/parcelles/" + p1.getId()).header("Authorization", "Bearer a"))
                .andExpect(status().isForbidden());
        api.perform(get("/api/parcelles/" + p1.getId()).header("Authorization", "Bearer b"))
                .andExpect(status().isOk());
        api.perform(get("/api/parcelles/" + p2.getId()).header("Authorization", "Bearer a"))
                .andExpect(status().isOk());
    }

    @Test
    void agriculteurEtAgronomeNePeuventPasAdministrer() throws Exception {
        for (String compte : new String[]{"a", "agronome"}) {
            String bearer = "Bearer " + compte;
            api.perform(get("/api/utilisateurs").header("Authorization", bearer)).andExpect(status().isForbidden());
            api.perform(get("/api/affectations").header("Authorization", bearer)).andExpect(status().isForbidden());
            api.perform(post("/api/affectations").header("Authorization", bearer)
                            .contentType("application/json").content(demande(a.getId(), p3.getId())))
                    .andExpect(status().isForbidden());
            api.perform(delete("/api/affectations/" + affectationA1.getId()).header("Authorization", bearer))
                    .andExpect(status().isForbidden());
            api.perform(post("/api/exploitations").header("Authorization", bearer)
                            .contentType("application/json").content("{\"nom\":\"Interdit\",\"superficie\":4}"))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/parcelles/" + p1.getId()).header("Authorization", bearer)
                            .contentType("application/json")
                            .content("{\"nom\":\"Interdit\",\"superficie\":4,\"exploitationId\":" + sfax.getId() + "}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void refuserCompteDesactiveOuRoleNonAffectable() throws Exception {
        UserRepresentation inactif = new UserRepresentation();
        inactif.setEnabled(false);
        when(comptes.consulter("a")).thenReturn(inactif);
        api.perform(post("/api/affectations").header("Authorization", "Bearer admin")
                        .contentType("application/json").content(demande(a.getId(), p3.getId())))
                .andExpect(status().isBadRequest());
        when(comptes.consulterRole("b")).thenReturn(Role.ADMIN);
        api.perform(post("/api/affectations").header("Authorization", "Bearer admin")
                        .contentType("application/json").content(demande(b.getId(), p2.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validerIdentifiantsEtRessourcesAbsentes() throws Exception {
        api.perform(post("/api/affectations").header("Authorization", "Bearer admin")
                        .contentType("application/json").content(demande(-1L, p3.getId())))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/affectations").header("Authorization", "Bearer admin")
                        .contentType("application/json").content(demande(999999L, p3.getId())))
                .andExpect(status().isNotFound());
        api.perform(delete("/api/affectations/999999").header("Authorization", "Bearer admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void compteDesactiveOuRoleRetireNeConservePasSonAncienAcces() throws Exception {
        UserRepresentation inactif = new UserRepresentation();
        inactif.setEnabled(false);
        when(comptes.consulter("a")).thenReturn(inactif);
        api.perform(get("/api/parcelles").header("Authorization", "Bearer a")).andExpect(status().isUnauthorized());
        when(comptes.consulterRole("admin")).thenReturn(Role.AGRICULTEUR);
        api.perform(get("/api/affectations").header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
        when(comptes.consulterRole("b")).thenReturn(null);
        api.perform(get("/api/parcelles").header("Authorization", "Bearer b")).andExpect(status().isForbidden());
    }

    @Test
    void refuserTokensAbsentsInvalidesEtKeycloakIndisponible() throws Exception {
        api.perform(get("/api/parcelles")).andExpect(status().isUnauthorized());
        when(tokens.verifier("incorrect")).thenThrow(new java.text.ParseException("JWT incorrect", 0));
        api.perform(get("/api/parcelles").header("Authorization", "Bearer incorrect")).andExpect(status().isUnauthorized());
        when(comptes.consulter("a")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        api.perform(get("/api/parcelles").header("Authorization", "Bearer a")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void paginationAdministrateurTrieSansChevauchement() throws Exception {
        api.perform(get("/api/parcelles/pagination").param("page", "0").param("taille", "2")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu", hasSize(2)))
                .andExpect(jsonPath("$.contenu[0].id").value(p3.getId()))
                .andExpect(jsonPath("$.contenu[1].id").value(p2.getId()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.perform(get("/api/parcelles/pagination").param("page", "1").param("taille", "2")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].id").value(p1.getId()))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void paginationNeCompteQueLesParcellesAutorisees() throws Exception {
        api.perform(get("/api/parcelles/pagination").param("page", "0").param("taille", "1")
                        .header("Authorization", "Bearer a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.contenu[0].id").value(p2.getId()));
        api.perform(get("/api/parcelles/pagination").param("page", "1").param("taille", "1")
                        .header("Authorization", "Bearer a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].id").value(p1.getId()));
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer sans-affectation"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void paginationExploitationsEviteDoublonsEtRespecteAffectations() throws Exception {
        api.perform(get("/api/exploitations/pagination").param("taille", "1")
                        .header("Authorization", "Bearer a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].id").value(sfax.getId()));
        api.perform(get("/api/exploitations/pagination").param("page", "1").param("taille", "1")
                        .header("Authorization", "Bearer b"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.contenu[0].id").value(sfax.getId()));
    }

    @Test
    void paginationAffectationsFiltreAvantDeCompter() throws Exception {
        api.perform(get("/api/affectations/pagination").param("taille", "2")
                        .param("parcelleId", p1.getId().toString()).header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.contenu[*].parcelleId", everyItem(is(p1.getId().intValue()))));
        api.perform(get("/api/affectations/pagination").param("page", "1").param("taille", "2")
                        .param("parcelleId", p1.getId().toString()).header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].parcelleId").value(p1.getId()));
    }

    @Test
    void paginationUtilisateursNeCompleteQueLesComptesDeLaPage() throws Exception {
        api.perform(get("/api/utilisateurs/pagination").param("taille", "1")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].id").value(agronome.getId()))
                .andExpect(jsonPath("$.totalElements").value(3));
        org.mockito.Mockito.verify(comptes, org.mockito.Mockito.never()).consulter("a");
        org.mockito.Mockito.verify(comptes, org.mockito.Mockito.never()).consulter("b");
        for (String endpoint : new String[]{"utilisateurs", "affectations"}) {
            for (String compte : new String[]{"a", "agronome"}) {
                api.perform(get("/api/" + endpoint + "/pagination").header("Authorization", "Bearer " + compte))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void paginationValideBornesEtGerePagesVides() throws Exception {
        for (String endpoint : new String[]{"utilisateurs", "affectations", "parcelles", "exploitations"}) {
            String url = "/api/" + endpoint + "/pagination";
            api.perform(get(url).param("page", "-1").header("Authorization", "Bearer admin"))
                    .andExpect(status().isBadRequest());
            api.perform(get(url).param("taille", "0").header("Authorization", "Bearer admin"))
                    .andExpect(status().isBadRequest());
            api.perform(get(url).param("taille", "101").header("Authorization", "Bearer admin"))
                    .andExpect(status().isBadRequest());
            api.perform(get(url).param("page", "100").header("Authorization", "Bearer admin"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.contenu", hasSize(0)));
            api.perform(get(url)).andExpect(status().isUnauthorized());
        }
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

    private String demande(Long utilisateurId, Long parcelleId) {
        return "{\"utilisateurId\":" + utilisateurId + ",\"parcelleId\":" + parcelleId + "}";
    }
}
