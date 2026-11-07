package com.example.backend;

import com.example.backend.support.KeycloakPanneProxy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Recette optionnelle avec deux services réels, exclusivement en environnement jetable. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "SMARTAGRI_REAL_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecetteKeycloakTests {
    private static KeycloakPanneProxy relais;
    private static String base;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLIENT_TEST = "smartagri-recette";
    private static final String PREFIXE = "recette-";
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.example.backend.repository.CampagneRepository campagnes;
    private Keycloak administrateur;
    private RealmResource realm;
    private String adminToken;

    @DynamicPropertySource
    static void configurer(DynamicPropertyRegistry proprietes) throws Exception {
        base = obligatoire("SMARTAGRI_TEST_KEYCLOAK_URL");
        String bdd = obligatoire("SMARTAGRI_TEST_DATABASE_URL");
        // Ne jamais exécuter le nettoyage de recette sur les services habituels.
        if (!base.startsWith("http://127.0.0.1:") || URI.create(base).getPort() == 8082
                || !bdd.startsWith("jdbc:postgresql://127.0.0.1:")
                || !bdd.endsWith("/smartagri_recette")) {
            throw new IllegalArgumentException("La recette nécessite les services temporaires dédiés.");
        }
        relais = new KeycloakPanneProxy(base);
        proprietes.add("spring.datasource.url", () -> bdd);
        proprietes.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        proprietes.add("spring.datasource.username", () -> "smartagri_recette");
        proprietes.add("spring.datasource.password", () -> obligatoire("SMARTAGRI_TEST_DATABASE_PASSWORD"));
        proprietes.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        proprietes.add("app.keycloak.issuer", () -> base + "/realms/smartagri");
        proprietes.add("app.keycloak.server-url", relais::adresse);
        proprietes.add("app.keycloak.admin-client-secret", () -> obligatoire("KEYCLOAK_ADMIN_CLIENT_SECRET"));
        proprietes.add("logging.level.root", () -> "ERROR");
        proprietes.add("logging.level.org.hibernate", () -> "OFF");
        proprietes.add("logging.level.org.springframework", () -> "ERROR");
        proprietes.add("debug", () -> "false");
    }

    @BeforeAll
    void preparerRealm() throws Exception {
        administrateur = KeycloakBuilder.builder().serverUrl(base).realm("master").clientId("admin-cli")
                .grantType(OAuth2Constants.PASSWORD).username("import-test-admin")
                .password(obligatoire("KC_BOOTSTRAP_ADMIN_PASSWORD")).build();
        realm = administrateur.realm("smartagri");
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(CLIENT_TEST);
        client.setEnabled(true);
        client.setPublicClient(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setStandardFlowEnabled(false);
        client.setProtocol("openid-connect");
        ProtocolMapperRepresentation audience = new ProtocolMapperRepresentation();
        audience.setName("audience-recette");
        audience.setProtocol("openid-connect");
        audience.setProtocolMapper("oidc-audience-mapper");
        audience.setConfig(Map.of("included.custom.audience", "smartagri-api", "access.token.claim", "true"));
        client.setProtocolMappers(List.of(audience));
        try (Response reponse = realm.clients().create(client)) {
            assertThat(reponse.getStatus()).isEqualTo(201);
        }
        // Contrainte uniquement dans la base jetable pour provoquer un vrai refus SQL.
        jdbc.execute("alter table utilisateurs add constraint refus_recette check (nom <> 'REFUS_BDD_TEST')");
        String motDePasse = UUID.randomUUID().toString();
        creerCompteDirect(PREFIXE + "administrateur", "ADMIN", motDePasse);
        adminToken = token(PREFIXE + "administrateur", motDePasse);
    }

    @BeforeEach
    void preparerCas() {
        relais.reinitialiser();
        jdbc.update("delete from traitements");
        jdbc.update("delete from fertilisations");
        jdbc.update("delete from irrigations");
        jdbc.update("delete from observations");
        jdbc.update("delete from plantations");
        jdbc.update("delete from campagne_parcelles");
        jdbc.update("delete from campagnes");
        jdbc.update("delete from affectations");
        jdbc.update("delete from parcelles");
        jdbc.update("delete from exploitations");
        jdbc.update("delete from utilisateurs");
        jdbc.update("delete from varietes");
        jdbc.update("delete from cultures");
        for (UserRepresentation utilisateur : realm.users().list()) {
            if (utilisateur.getUsername().startsWith(PREFIXE)
                    && !utilisateur.getUsername().equals(PREFIXE + "administrateur")) {
                realm.users().get(utilisateur.getId()).remove();
            }
        }
    }

    @AfterAll
    void fermer() {
        if (administrateur != null) administrateur.close();
        if (relais != null) relais.close();
    }

    @Test
    void creationModificationEtActivationAvecVraiKeycloak() throws Exception {
        JsonNode utilisateur = creer("cycle", "Ancien", "AGRICULTEUR");
        String id = utilisateur.get("keycloakId").asText();
        assertThat(compte("cycle").isEnabled()).isTrue();
        assertThat(role(id)).isEqualTo("AGRICULTEUR");
        api.perform(put("/api/utilisateurs/" + utilisateur.get("id").asLong())
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(modification("Nouveau", "AGRONOME")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Nouveau"))
                .andExpect(jsonPath("$.role").value("AGRONOME"));
        assertThat(realm.users().get(id).toRepresentation().getLastName()).isEqualTo("Nouveau");
        assertThat(role(id)).isEqualTo("AGRONOME");
        api.perform(put("/api/utilisateurs/" + utilisateur.get("id").asLong() + "/desactiver")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        assertThat(realm.users().get(id).toRepresentation().isEnabled()).isFalse();
    }

    @Test
    void doublonDansKeycloakNeCreePasDeProfil() throws Exception {
        creer("doublon", "Ancien", "AGRICULTEUR");
        api.perform(post("/api/utilisateurs").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(creation("doublon", "Autre", "AGRICULTEUR", "autre@example.test")))
                .andExpect(status().isConflict());
        assertThat(nombreProfils()).isEqualTo(1);
        assertThat(realm.users().searchByUsername(PREFIXE + "doublon", true)).hasSize(1);
    }

    @Test
    void refusSqlAnnuleLaCreationDansKeycloak() throws Exception {
        api.perform(post("/api/utilisateurs").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(creation("sql", "REFUS_BDD_TEST", "AGRICULTEUR", null)))
                .andExpect(status().isConflict());
        assertThat(nombreProfils()).isZero();
        assertThat(realm.users().searchByUsername(PREFIXE + "sql", true)).isEmpty();
    }

    @Test
    void refusSqlRestaureLeCompteEtSonRole() throws Exception {
        JsonNode utilisateur = creer("restauration", "Ancien", "AGRICULTEUR");
        api.perform(put("/api/utilisateurs/" + utilisateur.get("id").asLong())
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(modification("REFUS_BDD_TEST", "AGRONOME")))
                .andExpect(status().isConflict());
        String id = utilisateur.get("keycloakId").asText();
        assertThat(realm.users().get(id).toRepresentation().getLastName()).isEqualTo("Ancien");
        assertThat(role(id)).isEqualTo("AGRICULTEUR");
        assertThat(jdbc.queryForObject("select nom from utilisateurs where id = ?", String.class,
                utilisateur.get("id").asLong())).isEqualTo("Ancien");
    }

    @Test
    void panneAffectationRoleAnnuleLeCompteCree() throws Exception {
        relais.echouer("POST", "/role-mappings/realm", "AGRICULTEUR", 503);
        api.perform(post("/api/utilisateurs").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(creation("role", "Test", "AGRICULTEUR", null)))
                .andExpect(status().isServiceUnavailable());
        assertThat(relais.nombrePannes()).isEqualTo(1);
        assertThat(nombreProfils()).isZero();
        assertThat(realm.users().searchByUsername(PREFIXE + "role", true)).isEmpty();
    }

    @Test
    void panneChangementRoleRestaureAnciennesInformations() throws Exception {
        JsonNode utilisateur = creer("role-modification", "Ancien", "AGRICULTEUR");
        relais.echouer("POST", "/role-mappings/realm", "AGRONOME", 503);
        api.perform(put("/api/utilisateurs/" + utilisateur.get("id").asLong())
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(modification("Nouveau", "AGRONOME")))
                .andExpect(status().isServiceUnavailable());
        assertThat(relais.nombrePannes()).isEqualTo(1);
        String id = utilisateur.get("keycloakId").asText();
        assertThat(realm.users().get(id).toRepresentation().getLastName()).isEqualTo("Ancien");
        assertThat(role(id)).isEqualTo("AGRICULTEUR");
        assertThat(jdbc.queryForObject("select nom from utilisateurs", String.class)).isEqualTo("Ancien");
    }

    @Test
    void activationIncertaineConserveUnProfilEtUnCompteDesactive() throws Exception {
        relais.echouer("PUT", "/users/", "\"enabled\":true", 503);
        api.perform(post("/api/utilisateurs").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(creation("activation", "Test", "AGRICULTEUR", null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("activation")));
        assertThat(relais.nombrePannes()).isEqualTo(1);
        assertThat(nombreProfils()).isEqualTo(1);
        assertThat(compte("activation").isEnabled()).isFalse();
        Long profilId = jdbc.queryForObject("select id from utilisateurs", Long.class);
        api.perform(put("/api/utilisateurs/" + profilId + "/activer")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));
        assertThat(compte("activation").isEnabled()).isTrue();
    }

    @Test
    void echecAnnulationSignaleCompteDesactiveAReprendre() throws Exception {
        relais.echouer("DELETE", "/users/", "", 503);
        api.perform(post("/api/utilisateurs").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(creation("annulation", "REFUS_BDD_TEST", "AGRICULTEUR", null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("vérifié")));
        assertThat(relais.nombrePannes()).isEqualTo(1);
        assertThat(nombreProfils()).isZero();
        assertThat(compte("annulation").isEnabled()).isFalse();
    }

    @Test
    void politiqueMotDePasseKeycloakRenvoyeeEn400() throws Exception {
        RealmRepresentation regles = new RealmRepresentation();
        regles.setPasswordPolicy("length(12)");
        realm.update(regles);
        try {
            ObjectNode demande = (ObjectNode) JSON.readTree(creation("motdepasse", "Test", "AGRICULTEUR", null));
            demande.put("motDePasse", "Test1234");
            api.perform(post("/api/utilisateurs").header("Authorization", "Bearer " + adminToken)
                            .contentType("application/json").content(JSON.writeValueAsString(demande)))
                    .andExpect(status().isBadRequest());
            assertThat(nombreProfils()).isZero();
            assertThat(realm.users().searchByUsername(PREFIXE + "motdepasse", true)).isEmpty();
        } finally {
            regles.setPasswordPolicy("");
            realm.update(regles);
        }
    }

    @Test
    void parcoursCompletAdministrateurAgriculteursEtAgronomes() throws Exception {
        CompteRecette a = creerCompteConnecte("agriculteur-a", "AGRICULTEUR");
        CompteRecette b = creerCompteConnecte("agriculteur-b", "AGRICULTEUR");
        CompteRecette agronome = creerCompteConnecte("agronome", "AGRONOME");
        CompteRecette secondAgronome = creerCompteConnecte("second-agronome", "AGRONOME");
        JsonNode sfax = creerRessource("/api/exploitations", Map.of("nom", "Sfax", "superficie", 20));
        JsonNode nabeul = creerRessource("/api/exploitations", Map.of("nom", "Nabeul", "superficie", 15));
        JsonNode p1 = creerParcelle("Oliviers 1", sfax);
        JsonNode p2 = creerParcelle("Oliviers 2", sfax);
        JsonNode p3 = creerParcelle("Maraichage", nabeul);
        assertThat(sfax.get("code").asText()).matches("EXP-[0-9]{2,}");
        assertThat(p1.get("code").asText()).matches("PAR-[0-9]{2,}");
        JsonNode affectationA1 = affecter(a, p1);
        JsonNode affectationA2 = affecter(a, p2);
        affecter(b, p1);
        affecter(b, p3);
        affecter(agronome, p1);
        affecter(agronome, p2);
        affecter(agronome, p3);
        affecter(secondAgronome, p1);

        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        api.perform(get("/api/affectations/pagination").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(8));
        api.perform(get("/api/utilisateurs/pagination").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + a.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.contenu[*].id", org.hamcrest.Matchers.containsInAnyOrder(
                        p1.get("id").asInt(), p2.get("id").asInt())));
        api.perform(get("/api/exploitations/pagination").header("Authorization", "Bearer " + a.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].id").value(sfax.get("id").asLong()));
        api.perform(get("/api/parcelles/" + p3.get("id").asLong()).header("Authorization", "Bearer " + a.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/exploitations/" + nabeul.get("id").asLong()).header("Authorization", "Bearer " + a.token))
                .andExpect(status().isForbidden());

        api.perform(get("/api/exploitations/pagination").header("Authorization", "Bearer " + b.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        api.perform(get("/api/parcelles/exploitation/" + sfax.get("id").asLong())
                        .header("Authorization", "Bearer " + b.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(p1.get("id").asLong()));
        api.perform(get("/api/parcelles/" + p2.get("id").asLong()).header("Authorization", "Bearer " + b.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        api.perform(get("/api/parcelles/" + p2.get("id").asLong())
                        .header("Authorization", "Bearer " + secondAgronome.token))
                .andExpect(status().isForbidden());

        for (CompteRecette personne : List.of(a, b, agronome, secondAgronome)) {
            api.perform(get("/api/utilisateurs/pagination").header("Authorization", "Bearer " + personne.token))
                    .andExpect(status().isForbidden());
            api.perform(get("/api/utilisateurs/" + a.id).header("Authorization", "Bearer " + personne.token))
                    .andExpect(status().isForbidden());
            api.perform(get("/api/affectations").header("Authorization", "Bearer " + personne.token))
                    .andExpect(status().isForbidden());
            api.perform(post("/api/exploitations").header("Authorization", "Bearer " + personne.token)
                            .contentType("application/json").content("{\"nom\":\"Interdit\",\"superficie\":5}"))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/parcelles/" + p1.get("id").asLong())
                            .header("Authorization", "Bearer " + personne.token).contentType("application/json")
                            .content(JSON.writeValueAsString(Map.of("nom", "Interdit", "superficie", 5,
                                    "exploitationId", sfax.get("id").asLong()))))
                    .andExpect(status().isForbidden());
            api.perform(delete("/api/affectations/" + affectationA1.get("id").asLong())
                            .header("Authorization", "Bearer " + personne.token))
                    .andExpect(status().isForbidden());
        }
        api.perform(post("/api/affectations").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(JSON.writeValueAsString(
                                Map.of("utilisateurId", a.id, "parcelleId", p1.get("id").asLong()))))
                .andExpect(status().isConflict());
        api.perform(delete("/api/affectations/" + affectationA1.get("id").asLong())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        api.perform(get("/api/parcelles/" + p1.get("id").asLong()).header("Authorization", "Bearer " + a.token))
                .andExpect(status().isForbidden());
        for (CompteRecette personne : List.of(b, agronome, secondAgronome)) {
            api.perform(get("/api/parcelles/" + p1.get("id").asLong())
                            .header("Authorization", "Bearer " + personne.token))
                    .andExpect(status().isOk());
        }
        api.perform(get("/api/parcelles/" + p2.get("id").asLong()).header("Authorization", "Bearer " + a.token))
                .andExpect(status().isOk());
        api.perform(delete("/api/affectations/" + affectationA2.get("id").asLong())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + a.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        api.perform(get("/api/exploitations/pagination").header("Authorization", "Bearer " + a.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(realm.users().get(a.keycloakId).toRepresentation().isEnabled()).isTrue();
        assertThat(nombreProfils()).isEqualTo(4);
    }

    @Test
    void compteDesactiveRefuseUnJwtEncoreValide() throws Exception {
        CompteRecette personne = creerCompteConnecte("session-desactivation", "AGRICULTEUR");
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isOk());
        api.perform(put("/api/utilisateurs/" + personne.id + "/desactiver")
                        .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isUnauthorized());
        api.perform(put("/api/utilisateurs/" + personne.id + "/activer")
                        .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        String nouveauToken = token(personne.username, personne.motDePasse);
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + nouveauToken))
                .andExpect(status().isOk());
    }

    @Test
    void retraitDuRoleAdministrateurRefuseUnAncienJwtAdmin() throws Exception {
        CompteRecette personne = creerCompteConnecte("session-role", "ADMIN");
        api.perform(get("/api/utilisateurs/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isOk());
        api.perform(put("/api/utilisateurs/" + personne.id).header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(modification("Agriculteur", "AGRICULTEUR")))
                .andExpect(status().isOk());
        api.perform(get("/api/utilisateurs/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/affectations/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rolesAmbigusPuisAbsentsRefusentUnAncienJwt() throws Exception {
        CompteRecette personne = creerCompteConnecte("session-roles-ambigus", "AGRONOME");
        realm.users().get(personne.keycloakId).roles().realmLevel()
                .add(List.of(realm.roles().get("AGRICULTEUR").toRepresentation()));
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isForbidden());
        realm.users().get(personne.keycloakId).roles().realmLevel().remove(List.of(
                realm.roles().get("AGRICULTEUR").toRepresentation(),
                realm.roles().get("AGRONOME").toRepresentation()));
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isForbidden());
    }

    @Test
    void indisponibiliteControleCompteEtPrevolCors() throws Exception {
        CompteRecette personne = creerCompteConnecte("session-panne", "AGRICULTEUR");
        relais.echouer("GET", "/users/" + personne.keycloakId, "", 503);
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isServiceUnavailable());
        api.perform(get("/api/parcelles/pagination").header("Authorization", "Bearer " + personne.token))
                .andExpect(status().isOk());
        api.perform(options("/api/parcelles/pagination").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        api.perform(get("/api/parcelles/pagination")).andExpect(status().isUnauthorized());
    }

    @Test
    void catalogueAgricoleAvecPostgreSqlEtLesTroisRoles() throws Exception {
        JsonNode culture = creerRessource("/api/cultures",
                Map.of("nom", "Olivier", "type", "Arboriculture"));
        long cultureId = culture.get("id").asLong();
        JsonNode variete = creerRessource("/api/varietes",
                Map.of("nom", "Chemlali", "cultureId", cultureId));
        CompteRecette agriculteur = creerCompteConnecte("catalogue-agriculteur", "AGRICULTEUR");
        CompteRecette agronome = creerCompteConnecte("catalogue-agronome", "AGRONOME");
        for (CompteRecette personne : List.of(agriculteur, agronome)) {
            api.perform(get("/api/cultures/pagination").header("Authorization", "Bearer " + personne.token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
            api.perform(get("/api/varietes/pagination").param("cultureId", Long.toString(cultureId))
                            .header("Authorization", "Bearer " + personne.token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].cultureNom").value("Olivier"));
            api.perform(post("/api/cultures").header("Authorization", "Bearer " + personne.token)
                            .contentType("application/json").content("{\"nom\":\"Interdit\"}"))
                    .andExpect(status().isForbidden());
            api.perform(put("/api/varietes/" + variete.get("id").asLong())
                            .header("Authorization", "Bearer " + personne.token).contentType("application/json")
                            .content(JSON.writeValueAsString(Map.of("nom", "Interdit", "cultureId", cultureId))))
                    .andExpect(status().isForbidden());
        }
        api.perform(post("/api/cultures").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"nom\":\"  OLIVIER  \"}"))
                .andExpect(status().isConflict());
        api.perform(post("/api/varietes").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(JSON.writeValueAsString(
                                Map.of("nom", " CHEMLALI ", "cultureId", cultureId))))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into cultures(nom, nom_normalise) values ('Doublon', 'olivier')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into varietes(nom, nom_normalise, culture_id) values ('Doublon', 'chemlali', ?)", cultureId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        api.perform(put("/api/cultures/" + cultureId).header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"nom\":\"Olivier Sfax\"}"))
                .andExpect(status().isOk());
        api.perform(get("/api/varietes/" + variete.get("id").asLong())
                        .header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cultureNom").value("Olivier Sfax"));
    }

    @Test
    void campagnesAvecPostgreSqlEtLesTroisRoles() throws Exception {
        JsonNode exploitation = creerRessource("/api/exploitations", Map.of("nom", "Site campagnes", "superficie", 20));
        JsonNode p1 = creerParcelle("Parcelle accessible", exploitation);
        JsonNode p2 = creerParcelle("Parcelle hors accès", exploitation);
        CompteRecette agriculteur = creerCompteConnecte("campagne-agriculteur", "AGRICULTEUR");
        CompteRecette agronome = creerCompteConnecte("campagne-agronome", "AGRONOME");
        JsonNode affectation = affecter(agriculteur, p1);
        affecter(agronome, p1);
        affecter(agronome, p2);
        JsonNode campagne = creerRessource("/api/campagnes", Map.of(
                "nom", "Campagne de recette 2027", "dateDebut", "2027-01-01", "dateFin", "2027-12-31",
                "parcelleIds", List.of(p1.get("id").asLong(), p2.get("id").asLong())));
        long id = campagne.get("id").asLong();
        long privee = creerRessource("/api/campagnes", Map.of(
                "nom", "Campagne privée", "dateDebut", "2027-01-01", "dateFin", "2027-12-31",
                "parcelleIds", List.of(p2.get("id").asLong()))).get("id").asLong();
        assertThat(campagne.get("statut").asText()).isEqualTo("PLANIFIEE");
        assertThat(campagne.get("parcelles").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select cast(date_debut as varchar) from campagnes where id = ?",
                String.class, id)).isEqualTo("2027-01-01");
        for (CompteRecette personne : List.of(agriculteur, agronome)) {
            api.perform(get("/api/campagnes/pagination").param("statut", "PLANIFIEE")
                            .header("Authorization", "Bearer " + personne.token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(personne == agronome ? 2 : 1));
            api.perform(put("/api/campagnes/" + id + "/statut")
                            .header("Authorization", "Bearer " + personne.token)
                            .contentType("application/json").content("{\"statut\":\"EN_COURS\"}"))
                    .andExpect(status().isForbidden());
        }
        String detail = api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcelleIds", org.hamcrest.Matchers.contains(p1.get("id").asInt())))
                .andExpect(jsonPath("$.parcelles", org.hamcrest.Matchers.hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain(p2.get("code").asText(), "Parcelle hors accès");
        api.perform(get("/api/campagnes/" + privee).header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isForbidden());
        api.perform(put("/api/campagnes/" + id + "/statut").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"statut\":\"EN_COURS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("EN_COURS"));
        com.example.backend.entity.Campagne ancienneVersion = campagnes.findById(id).orElseThrow();
        api.perform(put("/api/campagnes/" + id + "/statut").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"statut\":\"CLOTUREE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTUREE"));
        ancienneVersion.setDescription("Ancienne modification");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> campagnes.saveAndFlush(ancienneVersion))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        api.perform(put("/api/campagnes/" + id).header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(JSON.writeValueAsString(Map.of(
                                "nom", "Interdit", "dateDebut", "2027-01-01", "dateFin", "2027-12-31",
                                "parcelleIds", List.of(p1.get("id").asLong())))))
                .andExpect(status().isConflict());
        api.perform(delete("/api/affectations/" + affectation.get("id").asLong())
                        .header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/campagnes/pagination").header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        api.perform(get("/api/campagnes/" + id).header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.parcelles", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void plantationsSuiventLesAffectationsEtLaClotureAvecPostgres() throws Exception {
        CompteRecette agriculteur = creerCompteConnecte("plantation-agri", "AGRICULTEUR");
        CompteRecette agronome = creerCompteConnecte("plantation-agro", "AGRONOME");
        JsonNode exploitation = creerRessource("/api/exploitations", Map.of("nom", "Ferme", "superficie", 20));
        JsonNode parcelle = creerParcelle("Oliviers", exploitation);
        JsonNode affectation = affecter(agriculteur, parcelle);
        affecter(agronome, parcelle);
        JsonNode culture = creerRessource("/api/cultures", Map.of("nom", "Olivier"));
        JsonNode variete = creerRessource("/api/varietes", Map.of("nom", "Chemlali", "cultureId", culture.get("id").asLong()));
        long campagneId = creerRessource("/api/campagnes", Map.of("nom", "Suivi 2027",
                "dateDebut", "2027-01-01", "dateFin", "2027-12-31",
                "parcelleIds", List.of(parcelle.get("id").asLong()))).get("id").asLong();
        String demande = JSON.writeValueAsString(Map.of("parcelleId", parcelle.get("id").asLong(),
                "campagneId", campagneId, "varieteId", variete.get("id").asLong(),
                "datePlantation", "2010-01-01", "superficie", 2.0, "statut", "EN_COURS"));
        long id = JSON.readTree(api.perform(post("/api/plantations")
                .header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.cultureNom").value("Olivier"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        api.perform(get("/api/plantations/pagination").param("campagneId", Long.toString(campagneId))
                .header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        api.perform(put("/api/plantations/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande.replace("2.0", "3.0")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.superficie").value(3.0));
        api.perform(post("/api/plantations").header("Authorization", "Bearer " + agronome.token)
                .contentType("application/json").content(demande)).andExpect(status().isForbidden());
        for (String statut : List.of("EN_COURS", "CLOTUREE")) {
            api.perform(put("/api/campagnes/" + campagneId + "/statut")
                    .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                    .content(JSON.writeValueAsString(Map.of("statut", statut)))).andExpect(status().isOk());
        }
        api.perform(put("/api/plantations/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande)).andExpect(status().isConflict());
        api.perform(delete("/api/affectations/" + affectation.get("id").asLong())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());
        api.perform(get("/api/plantations/" + id).header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/plantations/pagination").header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        api.perform(get("/api/plantations/" + id).header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.campagneCloturee").value(true));
    }

    @Test
    void observationsAvecAuteurEtAccesReels() throws Exception {
        CompteRecette agriculteur = creerCompteConnecte("observation-agri", "AGRICULTEUR");
        CompteRecette agronome = creerCompteConnecte("observation-agro", "AGRONOME");
        JsonNode exploitation = creerRessource("/api/exploitations", Map.of("nom", "Ferme", "superficie", 20));
        JsonNode parcelle = creerParcelle("Oliviers", exploitation);
        JsonNode affectation = affecter(agriculteur, parcelle);
        affecter(agronome, parcelle);
        JsonNode culture = creerRessource("/api/cultures", Map.of("nom", "Olivier"));
        JsonNode variete = creerRessource("/api/varietes", Map.of("nom", "Chemlali", "cultureId", culture.get("id").asLong()));
        long campagneId = creerRessource("/api/campagnes", Map.of("nom", "Suivi 2020",
                "dateDebut", "2020-01-01", "dateFin", "2020-12-31", "parcelleIds", List.of(parcelle.get("id").asLong())))
                .get("id").asLong();
        long plantationId = JSON.readTree(api.perform(post("/api/plantations")
                .header("Authorization", "Bearer " + agriculteur.token).contentType("application/json")
                .content(JSON.writeValueAsString(Map.of("parcelleId", parcelle.get("id").asLong(), "campagneId", campagneId,
                        "varieteId", variete.get("id").asLong(), "datePlantation", "2010-01-01", "superficie", 2,
                        "statut", "EN_COURS")))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String demande = JSON.writeValueAsString(Map.of("plantationId", plantationId,
                "dateObservation", "2020-06-01", "description", "Feuilles jaunes", "auteurNom", "Usurpation"));
        long id = JSON.readTree(api.perform(post("/api/observations")
                .header("Authorization", "Bearer " + agriculteur.token).contentType("application/json").content(demande))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.auteurNom").value("Recette"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        api.perform(get("/api/observations/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agronome.token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        api.perform(put("/api/observations/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande.replace("Feuilles jaunes", "Observation corrigee")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.auteurNom").value("Recette"));
        api.perform(post("/api/observations").header("Authorization", "Bearer " + agronome.token)
                .contentType("application/json").content(demande)).andExpect(status().isForbidden());
        for (String statut : List.of("EN_COURS", "CLOTUREE")) {
            api.perform(put("/api/campagnes/" + campagneId + "/statut").header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(JSON.writeValueAsString(Map.of("statut", statut))))
                    .andExpect(status().isOk());
        }
        api.perform(put("/api/observations/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande)).andExpect(status().isConflict());
        api.perform(delete("/api/affectations/" + affectation.get("id").asLong())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());
        api.perform(get("/api/observations/" + id).header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/observations/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agriculteur.token)).andExpect(status().isForbidden());
        api.perform(get("/api/observations/" + id).header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.campagneCloturee").value(true));
    }

    @Test
    void irrigationsAvecAuteurEtAccesReels() throws Exception {
        CompteRecette agriculteur = creerCompteConnecte("irrigation-agri", "AGRICULTEUR");
        CompteRecette agronome = creerCompteConnecte("irrigation-agro", "AGRONOME");
        JsonNode exploitation = creerRessource("/api/exploitations", Map.of("nom", "Ferme", "superficie", 20));
        JsonNode parcelle = creerParcelle("Oliviers", exploitation);
        JsonNode affectation = affecter(agriculteur, parcelle);
        affecter(agronome, parcelle);
        JsonNode culture = creerRessource("/api/cultures", Map.of("nom", "Olivier"));
        JsonNode variete = creerRessource("/api/varietes", Map.of("nom", "Chemlali", "cultureId", culture.get("id").asLong()));
        long campagneId = creerRessource("/api/campagnes", Map.of("nom", "Suivi 2020",
                "dateDebut", "2020-01-01", "dateFin", "2020-12-31", "parcelleIds", List.of(parcelle.get("id").asLong())))
                .get("id").asLong();
        long plantationId = JSON.readTree(api.perform(post("/api/plantations")
                .header("Authorization", "Bearer " + agriculteur.token).contentType("application/json")
                .content(JSON.writeValueAsString(Map.of("parcelleId", parcelle.get("id").asLong(), "campagneId", campagneId,
                        "varieteId", variete.get("id").asLong(), "datePlantation", "2010-01-01", "superficie", 2,
                        "statut", "EN_COURS")))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String demande = JSON.writeValueAsString(Map.of("plantationId", plantationId,
                "dateIrrigation", "2020-06-01", "commentaire", "Matin", "auteurNom", "Usurpation", "volume", 500, "unite", "LITRE"));
        long id = JSON.readTree(api.perform(post("/api/irrigations")
                .header("Authorization", "Bearer " + agriculteur.token).contentType("application/json").content(demande))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.auteurNom").value("Recette"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        api.perform(get("/api/irrigations/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agronome.token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        api.perform(post("/api/irrigations").header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande.replace("\"volume\":500", "\"volume\":2")
                        .replace("LITRE", "METRE_CUBE"))).andExpect(status().isCreated());
        api.perform(get("/api/irrigations/total").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agronome.token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeTotalMetresCubes").value(2.5));
        api.perform(put("/api/irrigations/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande.replace("\"volume\":500", "\"volume\":1").replace("LITRE", "METRE_CUBE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.auteurNom").value("Recette"));
        api.perform(get("/api/irrigations/total").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agronome.token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeTotalMetresCubes").value(3));
        api.perform(post("/api/irrigations").header("Authorization", "Bearer " + agronome.token)
                .contentType("application/json").content(demande)).andExpect(status().isForbidden());
        for (String statut : List.of("EN_COURS", "CLOTUREE")) {
            api.perform(put("/api/campagnes/" + campagneId + "/statut").header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(JSON.writeValueAsString(Map.of("statut", statut))))
                    .andExpect(status().isOk());
        }
        api.perform(put("/api/irrigations/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande)).andExpect(status().isConflict());
        api.perform(delete("/api/affectations/" + affectation.get("id").asLong())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());
        api.perform(get("/api/irrigations/" + id).header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/irrigations/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agriculteur.token)).andExpect(status().isForbidden());
        api.perform(get("/api/irrigations/total").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agriculteur.token)).andExpect(status().isForbidden());
        api.perform(get("/api/irrigations/" + id).header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.campagneCloturee").value(true));
    }

    @Test
    void fertilisationsAvecAuteurEtAccesReels() throws Exception {
        CompteRecette agriculteur = creerCompteConnecte("fertilisation-agri", "AGRICULTEUR");
        CompteRecette agronome = creerCompteConnecte("fertilisation-agro", "AGRONOME");
        JsonNode exploitation = creerRessource("/api/exploitations", Map.of("nom", "Ferme", "superficie", 20));
        JsonNode parcelle = creerParcelle("Oliviers", exploitation);
        JsonNode affectation = affecter(agriculteur, parcelle);
        affecter(agronome, parcelle);
        JsonNode culture = creerRessource("/api/cultures", Map.of("nom", "Olivier"));
        JsonNode variete = creerRessource("/api/varietes", Map.of("nom", "Chemlali", "cultureId", culture.get("id").asLong()));
        long campagneId = creerRessource("/api/campagnes", Map.of("nom", "Suivi 2020",
                "dateDebut", "2020-01-01", "dateFin", "2020-12-31", "parcelleIds", List.of(parcelle.get("id").asLong())))
                .get("id").asLong();
        long plantationId = JSON.readTree(api.perform(post("/api/plantations")
                .header("Authorization", "Bearer " + agriculteur.token).contentType("application/json")
                .content(JSON.writeValueAsString(Map.of("parcelleId", parcelle.get("id").asLong(), "campagneId", campagneId,
                        "varieteId", variete.get("id").asLong(), "datePlantation", "2010-01-01", "superficie", 2,
                        "statut", "EN_COURS")))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String demande = JSON.writeValueAsString(Map.of("plantationId", plantationId,
                "dateFertilisation", "2020-06-01", "commentaire", "Feuilles jaunes", "auteurNom", "Usurpation", "nomProduit", "Produit test", "quantite", 2, "unite", "KILOGRAMME"));
        long id = JSON.readTree(api.perform(post("/api/fertilisations")
                .header("Authorization", "Bearer " + agriculteur.token).contentType("application/json").content(demande))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.auteurNom").value("Recette"))
                .andExpect(jsonPath("$.nomProduit").value("Produit test"))
                .andExpect(jsonPath("$.quantite").value(2))
                .andExpect(jsonPath("$.unite").value("KILOGRAMME"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        api.perform(get("/api/fertilisations/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agronome.token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        api.perform(put("/api/fertilisations/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande.replace("\"quantite\":2", "\"quantite\":3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.auteurNom").value("Recette"))
                .andExpect(jsonPath("$.quantite").value(3));
        api.perform(post("/api/fertilisations").header("Authorization", "Bearer " + agronome.token)
                .contentType("application/json").content(demande)).andExpect(status().isForbidden());
        for (String statut : List.of("EN_COURS", "CLOTUREE")) {
            api.perform(put("/api/campagnes/" + campagneId + "/statut").header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(JSON.writeValueAsString(Map.of("statut", statut))))
                    .andExpect(status().isOk());
        }
        api.perform(put("/api/fertilisations/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande)).andExpect(status().isConflict());
        api.perform(delete("/api/affectations/" + affectation.get("id").asLong())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());
        api.perform(get("/api/fertilisations/" + id).header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/fertilisations/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agriculteur.token)).andExpect(status().isForbidden());
        api.perform(get("/api/fertilisations/" + id).header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.campagneCloturee").value(true));
    }

    @Test
    void traitementsAvecAuteurEtAccesReels() throws Exception {
        CompteRecette agriculteur = creerCompteConnecte("traitement-agri", "AGRICULTEUR");
        CompteRecette agronome = creerCompteConnecte("traitement-agro", "AGRONOME");
        JsonNode exploitation = creerRessource("/api/exploitations", Map.of("nom", "Ferme", "superficie", 20));
        JsonNode parcelle = creerParcelle("Oliviers", exploitation);
        JsonNode affectation = affecter(agriculteur, parcelle);
        affecter(agronome, parcelle);
        JsonNode culture = creerRessource("/api/cultures", Map.of("nom", "Olivier"));
        JsonNode variete = creerRessource("/api/varietes", Map.of("nom", "Chemlali", "cultureId", culture.get("id").asLong()));
        long campagneId = creerRessource("/api/campagnes", Map.of("nom", "Suivi 2020",
                "dateDebut", "2020-01-01", "dateFin", "2020-12-31", "parcelleIds", List.of(parcelle.get("id").asLong())))
                .get("id").asLong();
        long plantationId = JSON.readTree(api.perform(post("/api/plantations")
                .header("Authorization", "Bearer " + agriculteur.token).contentType("application/json")
                .content(JSON.writeValueAsString(Map.of("parcelleId", parcelle.get("id").asLong(), "campagneId", campagneId,
                        "varieteId", variete.get("id").asLong(), "datePlantation", "2010-01-01", "superficie", 2,
                        "statut", "EN_COURS")))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String demande = JSON.writeValueAsString(Map.of("plantationId", plantationId,
                "dateTraitement", "2020-06-01", "commentaire", "Feuilles jaunes", "auteurNom", "Usurpation", "nomProduit", "Produit test", "quantite", 2, "unite", "KILOGRAMME", "cible", "Cible test"));
        long id = JSON.readTree(api.perform(post("/api/traitements")
                .header("Authorization", "Bearer " + agriculteur.token).contentType("application/json").content(demande))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.auteurNom").value("Recette"))
                .andExpect(jsonPath("$.nomProduit").value("Produit test"))
                .andExpect(jsonPath("$.quantite").value(2))
                .andExpect(jsonPath("$.unite").value("KILOGRAMME"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        api.perform(get("/api/traitements/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agronome.token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        api.perform(put("/api/traitements/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande.replace("\"quantite\":2", "\"quantite\":3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.auteurNom").value("Recette"))
                .andExpect(jsonPath("$.quantite").value(3));
        api.perform(post("/api/traitements").header("Authorization", "Bearer " + agronome.token)
                .contentType("application/json").content(demande)).andExpect(status().isForbidden());
        for (String statut : List.of("EN_COURS", "CLOTUREE")) {
            api.perform(put("/api/campagnes/" + campagneId + "/statut").header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(JSON.writeValueAsString(Map.of("statut", statut))))
                    .andExpect(status().isOk());
        }
        api.perform(put("/api/traitements/" + id).header("Authorization", "Bearer " + agriculteur.token)
                .contentType("application/json").content(demande)).andExpect(status().isConflict());
        api.perform(delete("/api/affectations/" + affectation.get("id").asLong())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());
        api.perform(get("/api/traitements/" + id).header("Authorization", "Bearer " + agriculteur.token))
                .andExpect(status().isForbidden());
        api.perform(get("/api/traitements/pagination").param("plantationId", Long.toString(plantationId))
                .header("Authorization", "Bearer " + agriculteur.token)).andExpect(status().isForbidden());
        api.perform(get("/api/traitements/" + id).header("Authorization", "Bearer " + agronome.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.campagneCloturee").value(true));
    }

    private JsonNode creerRessource(String chemin, Map<String, Object> contenu) throws Exception {
        MvcResult resultat = api.perform(post(chemin).header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(JSON.writeValueAsString(contenu)))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(resultat.getResponse().getContentAsString());
    }

    private JsonNode creerParcelle(String nom, JsonNode exploitation) throws Exception {
        return creerRessource("/api/parcelles", Map.of("nom", nom, "superficie", 5,
                "exploitationId", exploitation.get("id").asLong()));
    }

    private JsonNode affecter(CompteRecette personne, JsonNode parcelle) throws Exception {
        return creerRessource("/api/affectations",
                Map.of("utilisateurId", personne.id, "parcelleId", parcelle.get("id").asLong()));
    }

    private CompteRecette creerCompteConnecte(String suffixe, String role) throws Exception {
        ObjectNode demande = (ObjectNode) JSON.readTree(creation(suffixe, "Recette", role, null));
        MvcResult resultat = api.perform(post("/api/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(JSON.writeValueAsString(demande)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode profil = JSON.readTree(resultat.getResponse().getContentAsString());
        CompteRecette personne = new CompteRecette();
        personne.id = profil.get("id").asLong();
        personne.keycloakId = profil.get("keycloakId").asText();
        personne.username = demande.get("username").asText();
        personne.motDePasse = demande.get("motDePasse").asText();
        personne.token = token(personne.username, personne.motDePasse);
        return personne;
    }

    private static class CompteRecette {
        long id;
        String keycloakId;
        String username;
        String motDePasse;
        String token;
    }

    private JsonNode creer(String suffixe, String nom, String role) throws Exception {
        MvcResult resultat = api.perform(post("/api/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(creation(suffixe, nom, role, null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.motDePasse").doesNotExist())
                .andReturn();
        return JSON.readTree(resultat.getResponse().getContentAsString());
    }

    private String creation(String suffixe, String nom, String role, String email) throws Exception {
        return JSON.writeValueAsString(Map.of("username", PREFIXE + suffixe, "nom", nom, "prenom", "Recette",
                "email", email == null ? suffixe + "@example.test" : email, "role", role,
                "motDePasse", UUID.randomUUID().toString()));
    }

    private String modification(String nom, String role) throws Exception {
        return JSON.writeValueAsString(Map.of("nom", nom, "prenom", "Recette",
                "email", "modification@example.test", "role", role));
    }

    private UserRepresentation compte(String suffixe) {
        List<UserRepresentation> resultats = realm.users().searchByUsername(PREFIXE + suffixe, true);
        assertThat(resultats).hasSize(1);
        return resultats.getFirst();
    }

    private String role(String keycloakId) {
        List<String> roles = new java.util.ArrayList<>();
        for (RoleRepresentation role : realm.users().get(keycloakId).roles().realmLevel().listEffective()) {
            if (List.of("ADMIN", "AGRICULTEUR", "AGRONOME").contains(role.getName())) {
                roles.add(role.getName());
            }
        }
        assertThat(roles).hasSize(1);
        return roles.getFirst();
    }

    private long nombreProfils() {
        return jdbc.queryForObject("select count(*) from utilisateurs", Long.class);
    }

    private void creerCompteDirect(String username, String role, String motDePasse) {
        UserRepresentation utilisateur = new UserRepresentation();
        utilisateur.setUsername(username);
        utilisateur.setFirstName("Recette");
        utilisateur.setLastName("Administrateur");
        utilisateur.setEmail(username + "@example.test");
        utilisateur.setEmailVerified(true);
        utilisateur.setEnabled(true);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType("password");
        credential.setValue(motDePasse);
        credential.setTemporary(false);
        utilisateur.setCredentials(List.of(credential));
        try (Response reponse = realm.users().create(utilisateur)) {
            assertThat(reponse.getStatus()).isEqualTo(201);
            String id = CreatedResponseUtil.getCreatedId(reponse);
            realm.users().get(id).roles().realmLevel().add(List.of(realm.roles().get(role).toRepresentation()));
        }
    }

    private String token(String username, String motDePasse) throws Exception {
        String formulaire = "grant_type=password&client_id=" + CLIENT_TEST + "&username=" +
                java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8) + "&password=" +
                java.net.URLEncoder.encode(motDePasse, java.nio.charset.StandardCharsets.UTF_8);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> reponse = client.send(HttpRequest.newBuilder(
                            URI.create(base + "/realms/smartagri/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formulaire)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(reponse.statusCode()).isEqualTo(200);
            return JSON.readTree(reponse.body()).get("access_token").asText();
        }
    }

    private static String obligatoire(String nom) {
        String valeur = System.getenv(nom);
        if (valeur == null || valeur.isBlank()) throw new IllegalStateException("Variable de recette absente : " + nom);
        return valeur;
    }
}
