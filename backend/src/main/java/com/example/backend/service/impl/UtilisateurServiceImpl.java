package com.example.backend.service.impl;

import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.utilisateur.UtilisateurCreationRequest;
import com.example.backend.dto.utilisateur.UtilisateurModificationRequest;
import com.example.backend.dto.utilisateur.UtilisateurResponse;
import com.example.backend.entity.Utilisateur;
import com.example.backend.enums.Role;
import com.example.backend.repository.UtilisateurRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.CompteKeycloakService;
import com.example.backend.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UtilisateurServiceImpl implements UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;

    private final CompteKeycloakService compteKeycloakService;
    private final AutorisationService autorisationService;

    @Override
    
    public PageResponse<UtilisateurResponse> consulterPage(Pageable pagination) {
        autorisationService.verifierAdministrateur();
        Page<Utilisateur> resultat;
        resultat = utilisateurRepository.findAll(pagination);
        List<UtilisateurResponse> contenu = new ArrayList<>();
        for (Utilisateur element : resultat.getContent()) {
            
            contenu.add(completerResponse(element));
        }
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    @Override
    public UtilisateurResponse creer(UtilisateurCreationRequest request) {
        autorisationService.verifierAdministrateur();
        String email = normaliserEmail(request.getEmail());

        if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cette adresse email est déjà utilisée"
            );
        }

        // Le compte Keycloak est créé désactivé.
        String keycloakId = compteKeycloakService.creer(request);

        Utilisateur utilisateur = Utilisateur.builder()
                .keycloakId(keycloakId)
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .email(email)
                .build();

        UserRepresentation compte;

        try {
            compte = compteKeycloakService.consulter(keycloakId);

            utilisateur = utilisateurRepository.saveAndFlush(utilisateur);

        } catch (RuntimeException exception) {
            annulerCreationCompte(keycloakId);
            throw traduireErreurPersistance(exception);
        }

        try {
            compteKeycloakService.changerActivation(keycloakId, true);

        } catch (RuntimeException exception) {
            // Le profil est conservé pour permettre une reprise.
            try {
                compteKeycloakService.changerActivation(keycloakId, false);

            } catch (RuntimeException erreurDesactivation) {
                log.error(
                        "Activation incertaine du compte Keycloak {}",
                        keycloakId,
                        erreurDesactivation
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profil créé avec l’identifiant " + utilisateur.getId()
                            + ", mais activation du compte non confirmée. "
                            + "Vérifiez son état avant de réessayer."
            );
        }

        compte.setEnabled(true);

        return convertirEnResponse(
                utilisateur,
                compte,
                request.getRole()
        );
    }

    @Override
    public UtilisateurResponse modifier(
            Long id,
            UtilisateurModificationRequest request) {

        autorisationService.verifierAdministrateur();

        Utilisateur utilisateur = rechercherUtilisateur(id);

        String email = normaliserEmail(request.getEmail());

        if (utilisateurRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cette adresse email est déjà utilisée"
            );
        }

        String keycloakId = utilisateur.getKeycloakId();

        UserRepresentation ancienCompte =
                compteKeycloakService.consulter(keycloakId);

        Role ancienRole = compteKeycloakService.consulterRole(keycloakId);

        if (ancienRole == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Le compte doit posséder un rôle métier avant sa modification"
            );
        }

        UtilisateurModificationRequest anciennesInformations =
                new UtilisateurModificationRequest();

        anciennesInformations.setNom(ancienCompte.getLastName());
        anciennesInformations.setPrenom(ancienCompte.getFirstName());
        anciennesInformations.setEmail(ancienCompte.getEmail());
        anciennesInformations.setRole(ancienRole);

        if (
            anciennesInformations.getNom() == null ||
            anciennesInformations.getPrenom() == null ||
            anciennesInformations.getEmail() == null
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Le profil Keycloak est incomplet et doit être vérifié"
            );
        }

        compteKeycloakService.modifier(keycloakId, request);

        utilisateur.setNom(request.getNom().trim());
        utilisateur.setPrenom(request.getPrenom().trim());
        utilisateur.setEmail(email);

        try {
            utilisateur = utilisateurRepository.saveAndFlush(utilisateur);

        } catch (RuntimeException exception) {
            restaurerCompte(keycloakId, anciennesInformations);
            throw traduireErreurPersistance(exception);
        }

        return convertirEnResponse(
                utilisateur,
                ancienCompte,
                request.getRole()
        );
    }

    @Override
    public UtilisateurResponse consulterParId(Long id) {
        autorisationService.verifierAdministrateur();
        Utilisateur utilisateur = rechercherUtilisateur(id);

        return completerResponse(utilisateur);
    }

    @Override
    public List<UtilisateurResponse> consulterTous() {
        autorisationService.verifierAdministrateur();
        List<Utilisateur> utilisateurs = utilisateurRepository.findAll();

        List<UtilisateurResponse> responses = new ArrayList<>();

        for (Utilisateur utilisateur : utilisateurs) {
            responses.add(completerResponse(utilisateur));
        }

        return responses;
    }

    @Override
    public UtilisateurResponse activer(Long id) {
        autorisationService.verifierAdministrateur();
        return changerActivation(id, true);
    }

    @Override
    public UtilisateurResponse desactiver(Long id) {
        autorisationService.verifierAdministrateur();
        return changerActivation(id, false);
    }

    private UtilisateurResponse changerActivation(Long id, boolean actif) {
        Utilisateur utilisateur = rechercherUtilisateur(id);

        UserRepresentation compte =
                compteKeycloakService.consulter(utilisateur.getKeycloakId());

        Role role =
                compteKeycloakService.consulterRole(utilisateur.getKeycloakId());

        compteKeycloakService.changerActivation(
                utilisateur.getKeycloakId(),
                actif
        );

        compte.setEnabled(actif);

        return convertirEnResponse(utilisateur, compte, role);
    }

    private Utilisateur rechercherUtilisateur(Long id) {
        Optional<Utilisateur> resultat =
                utilisateurRepository.findById(id);

        if (resultat.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Utilisateur introuvable avec l’identifiant : " + id
            );
        }

        return resultat.get();
    }

    private UtilisateurResponse completerResponse(Utilisateur utilisateur) {
        UserRepresentation compte =
                compteKeycloakService.consulter(utilisateur.getKeycloakId());

        Role role =
                compteKeycloakService.consulterRole(utilisateur.getKeycloakId());

        return convertirEnResponse(utilisateur, compte, role);
    }

    private UtilisateurResponse convertirEnResponse(
            Utilisateur utilisateur,
            UserRepresentation compte,
            Role role) {

        UtilisateurResponse response = new UtilisateurResponse();

        response.setId(utilisateur.getId());
        response.setKeycloakId(utilisateur.getKeycloakId());
        response.setUsername(compte.getUsername());
        response.setNom(utilisateur.getNom());
        response.setPrenom(utilisateur.getPrenom());
        response.setEmail(utilisateur.getEmail());
        response.setRole(role);
        response.setActif(Boolean.TRUE.equals(compte.isEnabled()));

        return response;
    }

    private String normaliserEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void annulerCreationCompte(String keycloakId) {
        try {
            compteKeycloakService.supprimer(keycloakId);

        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 404) {
                return;
            }

            log.error(
                    "Compte Keycloak à vérifier après échec de création : {}",
                    keycloakId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Création incomplète : le compte Keycloak désactivé "
                            + "doit être vérifié par un administrateur"
            );
        }
    }

    private void restaurerCompte(
            String keycloakId,
            UtilisateurModificationRequest anciennesInformations) {

        try {
            compteKeycloakService.modifier(
                    keycloakId,
                    anciennesInformations
            );

        } catch (RuntimeException exception) {
            log.error(
                    "Échec de restauration du compte Keycloak {}",
                    keycloakId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Modification incomplète : vérifiez la cohérence "
                            + "du profil PostgreSQL et du compte Keycloak"
            );
        }
    }

    private ResponseStatusException traduireErreurPersistance(
            RuntimeException exception) {

        if (exception instanceof ResponseStatusException) {
            return (ResponseStatusException) exception;
        }

        if (exception instanceof DataIntegrityViolationException) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Les données du profil entrent en conflit "
                            + "avec les données existantes"
            );
        }

        log.error("Échec d’enregistrement du profil utilisateur", exception);

        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible d’enregistrer le profil utilisateur"
        );
    }
}
