package com.example.backend.service.impl;

import com.example.backend.enums.Role;
import com.example.backend.repository.AffectationRepository;
import com.example.backend.service.AutorisationService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AutorisationServiceImpl implements AutorisationService {

    private final HttpServletRequest request;
    private final AffectationRepository affectationRepository;

    @Override
    public String obtenirKeycloakId() {
        Object informations = request.getAttribute("utilisateurConnecte");
        if (!(informations instanceof JWTClaimsSet)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connexion requise");
        }
        String keycloakId = ((JWTClaimsSet) informations).getSubject();
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identité invalide");
        }
        return keycloakId;
    }

    @Override
    public Role obtenirRole() {
        Object role = request.getAttribute("roleConnecte");
        if (!(role instanceof Role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle métier requis");
        }
        return (Role) role;
    }

    @Override
    public boolean estAdministrateur() {
        return obtenirRole() == Role.ADMIN;
    }

    @Override
    public void verifierAdministrateur() {
        if (!estAdministrateur()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Action réservée aux administrateurs");
        }
    }

    @Override
    public void verifierAgriculteur() {
        if (obtenirRole() != Role.AGRICULTEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Action réservée aux agriculteurs");
        }
    }

    @Override
    public void verifierAccesParcelle(Long parcelleId) {
        if (!estAdministrateur() && !affectationRepository
                .existsByUtilisateur_KeycloakIdAndParcelle_Id(obtenirKeycloakId(), parcelleId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès à cette parcelle refusé");
        }
    }

    @Override
    public void verifierAccesCampagne(Long campagneId) {
        if (!estAdministrateur() && affectationRepository.compterAccesCampagne(
                obtenirKeycloakId(), campagneId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès à cette campagne refusé");
        }
    }

    @Override
    public void verifierAccesExploitation(Long exploitationId) {
        if (!estAdministrateur() && !affectationRepository
                .existsByUtilisateur_KeycloakIdAndParcelle_Exploitation_Id(
                        obtenirKeycloakId(), exploitationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Accès à cette exploitation refusé");
        }
    }
}
