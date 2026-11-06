package com.example.backend.service.impl;

import com.example.backend.dto.utilisateur.UtilisateurCreationRequest;
import com.example.backend.service.CompteKeycloakService;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import com.example.backend.dto.utilisateur.UtilisateurModificationRequest;
import com.example.backend.enums.Role;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompteKeycloakServiceImpl implements CompteKeycloakService {

    private final Keycloak keycloakAdmin;

    @Value("${app.keycloak.realm}")
    private String realmName;

    @Override
    public String creer(UtilisateurCreationRequest request) {
        String keycloakId = null;

        try {
            RealmResource realm = keycloakAdmin.realm(realmName);

            RoleRepresentation role = realm.roles()
                    .get(request.getRole().name())
                    .toRepresentation();

            CredentialRepresentation motDePasse =
                    new CredentialRepresentation();

            motDePasse.setType(CredentialRepresentation.PASSWORD);
            motDePasse.setValue(request.getMotDePasse());
            motDePasse.setTemporary(false);

            UserRepresentation compte = new UserRepresentation();

            compte.setUsername(request.getUsername().trim());
            compte.setFirstName(request.getPrenom().trim());
            compte.setLastName(request.getNom().trim());
            compte.setEmail(
                    request.getEmail().trim().toLowerCase(Locale.ROOT)
            );

            // Activation après l’enregistrement du profil PostgreSQL.
            compte.setEnabled(false);

            compte.setCredentials(List.of(motDePasse));

            try (Response response = realm.users().create(compte)) {
                if (response.getStatus() == 409) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "L’identifiant ou l’email est déjà utilisé"
                    );
                }

                if (response.getStatus() == 400) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Keycloak refuse les données du compte"
                    );
                }

                if (response.getStatus() != 201) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Impossible de créer le compte dans Keycloak"
                    );
                }

                keycloakId = CreatedResponseUtil.getCreatedId(response);
            }

            realm.users()
                    .get(keycloakId)
                    .roles()
                    .realmLevel()
                    .add(List.of(role));

            return keycloakId;

        } catch (RuntimeException exception) {
            if (keycloakId != null) {
                annulerCreation(keycloakId);
            }

            throw traduireErreur(exception);
        }
    }

    @Override
    public UserRepresentation consulter(String keycloakId) {
        try {
            return keycloakAdmin.realm(realmName)
                    .users()
                    .get(keycloakId)
                    .toRepresentation();

        } catch (RuntimeException exception) {
            throw traduireErreur(exception);
        }
    }

    @Override
    public void changerActivation(String keycloakId, boolean actif) {
        try {
            UserResource utilisateur = keycloakAdmin.realm(realmName)
                    .users()
                    .get(keycloakId);

            UserRepresentation modification = new UserRepresentation();
            modification.setEnabled(actif);

            utilisateur.update(modification);

            if (!actif) {
                utilisateur.logout();
            }

        } catch (RuntimeException exception) {
            throw traduireErreur(exception);
        }
    }

    @Override
    public void supprimer(String keycloakId) {
        try {
            keycloakAdmin.realm(realmName)
                    .users()
                    .get(keycloakId)
                    .remove();

        } catch (RuntimeException exception) {
            throw traduireErreur(exception);
        }
    }

    private void annulerCreation(String keycloakId) {
        try {
            supprimer(keycloakId);

        } catch (RuntimeException exception) {
            log.error(
                    "Échec de l’annulation du compte Keycloak {}",
                    keycloakId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Création incomplète : un compte désactivé doit être vérifié dans Keycloak"
            );
        }
    }

    private ResponseStatusException traduireErreur(
            RuntimeException exception) {

        if (exception instanceof ResponseStatusException) {
            return (ResponseStatusException) exception;
        }

        if (exception instanceof WebApplicationException) {
            WebApplicationException erreur =
                    (WebApplicationException) exception;

            if (erreur.getResponse().getStatus() == 404) {
                return new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Le compte ou le rôle demandé est introuvable dans Keycloak"
                );
            }

            if (erreur.getResponse().getStatus() == 409) {
                return new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "L’identifiant ou l’email est déjà utilisé"
                );
            }

            if (erreur.getResponse().getStatus() == 400) {
                return new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Keycloak refuse les données envoyées"
                );
            }
        }

        if (exception instanceof WebApplicationException ||
                exception instanceof ProcessingException) {

            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Impossible de communiquer avec Keycloak ou permissions insuffisantes"
            );
        }

        log.error("Erreur interne pendant une opération Keycloak", exception);

        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur pendant la gestion du compte"
        );
    }

    @Override
public Role consulterRole(String keycloakId) {
    try {
        List<RoleRepresentation> roles = keycloakAdmin.realm(realmName)
                .users()
                .get(keycloakId)
                .roles()
                .realmLevel()
                .listEffective();

        Role resultat = null;

        for (RoleRepresentation roleKeycloak : roles) {
            for (Role roleMetier : Role.values()) {
                if (roleMetier.name().equals(roleKeycloak.getName())) {
                    if (resultat != null && resultat != roleMetier) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Le compte possède plusieurs rôles métier"
                        );
                    }

                    resultat = roleMetier;
                }
            }
        }

        return resultat;

    } catch (RuntimeException exception) {
        throw traduireErreur(exception);
    }
}

private List<RoleRepresentation> obtenirRolesMetier(
        UserResource utilisateur) {

    List<RoleRepresentation> roles = utilisateur.roles()
            .realmLevel()
            .listAll();

    List<RoleRepresentation> rolesMetier = new ArrayList<>();

    for (RoleRepresentation roleKeycloak : roles) {
        for (Role roleMetier : Role.values()) {
            if (roleMetier.name().equals(roleKeycloak.getName())) {
                rolesMetier.add(roleKeycloak);
                break;
            }
        }
    }

    return rolesMetier;
}

@Override
public void modifier(
        String keycloakId,
        UtilisateurModificationRequest request) {

    try {
        RealmResource realm = keycloakAdmin.realm(realmName);
        UserResource utilisateur = realm.users().get(keycloakId);

        UserRepresentation ancienCompte = utilisateur.toRepresentation();

        List<RoleRepresentation> anciensRoles =
                obtenirRolesMetier(utilisateur);

        RoleRepresentation nouveauRole = realm.roles()
                .get(request.getRole().name())
                .toRepresentation();

        UserRepresentation modification = new UserRepresentation();

        modification.setFirstName(request.getPrenom().trim());
        modification.setLastName(request.getNom().trim());
        modification.setEmail(
                request.getEmail().trim().toLowerCase(Locale.ROOT)
        );

        try {
            utilisateur.update(modification);

            if (!anciensRoles.isEmpty()) {
                utilisateur.roles().realmLevel().remove(anciensRoles);
            }

            utilisateur.roles().realmLevel().add(List.of(nouveauRole));

        } catch (RuntimeException exception) {
            try {
                restaurerCompte(
                        utilisateur,
                        ancienCompte,
                        anciensRoles
                );

            } catch (RuntimeException erreurRestauration) {
                log.error(
                        "Échec de restauration du compte Keycloak {}",
                        keycloakId,
                        erreurRestauration
                );

                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Modification incomplète : le compte doit être vérifié dans Keycloak"
                );
            }

            throw exception;
        }

    } catch (RuntimeException exception) {
        throw traduireErreur(exception);
    }
  }
    private void restaurerCompte(
        UserResource utilisateur,
        UserRepresentation ancienCompte,
        List<RoleRepresentation> anciensRoles) {

    utilisateur.update(ancienCompte);

    List<RoleRepresentation> rolesActuels =
            obtenirRolesMetier(utilisateur);

    if (!rolesActuels.isEmpty()) {
        utilisateur.roles().realmLevel().remove(rolesActuels);
    }

    if (!anciensRoles.isEmpty()) {
        utilisateur.roles().realmLevel().add(anciensRoles);
    }
}
}

