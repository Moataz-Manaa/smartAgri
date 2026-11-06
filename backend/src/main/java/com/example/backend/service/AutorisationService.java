package com.example.backend.service;

import com.example.backend.enums.Role;

public interface AutorisationService {

    String obtenirKeycloakId();

    Role obtenirRole();

    boolean estAdministrateur();

    void verifierAdministrateur();
    void verifierAgriculteur();

    void verifierAccesParcelle(Long parcelleId);
    void verifierAccesCampagne(Long campagneId);

    void verifierAccesExploitation(Long exploitationId);
}
