package com.example.backend.service;

import com.example.backend.dto.utilisateur.UtilisateurCreationRequest;
import org.keycloak.representations.idm.UserRepresentation;
import com.example.backend.dto.utilisateur.UtilisateurModificationRequest;
import com.example.backend.enums.Role;

public interface CompteKeycloakService {

    String creer(UtilisateurCreationRequest request);

    UserRepresentation consulter(String keycloakId);

    void changerActivation(String keycloakId, boolean actif);

    void supprimer(String keycloakId);

    Role consulterRole(String keycloakId);

void modifier(
        String keycloakId,
        UtilisateurModificationRequest request
);
}