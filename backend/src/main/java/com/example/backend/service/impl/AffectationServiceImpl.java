package com.example.backend.service.impl;

import com.example.backend.dto.affectation.AffectationRequest;
import com.example.backend.dto.affectation.AffectationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Affectation;
import com.example.backend.entity.Parcelle;
import com.example.backend.entity.Utilisateur;
import com.example.backend.enums.Role;
import com.example.backend.repository.AffectationRepository;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.repository.UtilisateurRepository;
import com.example.backend.service.AffectationService;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.CompteKeycloakService;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AffectationServiceImpl implements AffectationService {

    private final AffectationRepository affectationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ParcelleRepository parcelleRepository;
    private final CompteKeycloakService compteKeycloakService;
    private final AutorisationService autorisationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AffectationResponse> consulterPage(Pageable pagination, Long parcelleId) {
        autorisationService.verifierAdministrateur();
        Page<Affectation> resultat;
        if (parcelleId == null) {
            resultat = affectationRepository.findAll(pagination);
        } else {
            if (!parcelleRepository.existsById(parcelleId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable");
            }
            resultat = affectationRepository.findByParcelle_Id(parcelleId, pagination);
        }
        List<AffectationResponse> contenu = new ArrayList<>();
        for (Affectation element : resultat.getContent()) {
            Role role = compteKeycloakService.consulterRole(element.getUtilisateur().getKeycloakId());
            contenu.add(convertirEnResponse(element, role));
        }
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    @Override
    public AffectationResponse creer(AffectationRequest request) {
        autorisationService.verifierAdministrateur();

        Optional<Utilisateur> utilisateurTrouve = utilisateurRepository.findById(request.getUtilisateurId());
        if (utilisateurTrouve.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable");
        }
        Optional<Parcelle> parcelleTrouvee = parcelleRepository.findById(request.getParcelleId());
        if (parcelleTrouvee.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable");
        }

        Utilisateur utilisateur = utilisateurTrouve.get();
        UserRepresentation compte = compteKeycloakService.consulter(utilisateur.getKeycloakId());
        Role role = compteKeycloakService.consulterRole(utilisateur.getKeycloakId());
        if (!Boolean.TRUE.equals(compte.isEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Activez le compte avant de lui affecter une parcelle");
        }
        if (role != Role.AGRICULTEUR && role != Role.AGRONOME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seuls les agriculteurs et les agronomes peuvent être affectés");
        }
        if (affectationRepository.existsByUtilisateur_IdAndParcelle_Id(
                utilisateur.getId(), request.getParcelleId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette affectation existe déjà");
        }

        Affectation affectation = Affectation.builder()
                .utilisateur(utilisateur).parcelle(parcelleTrouvee.get()).build();
        try {
            affectation = affectationRepository.saveAndFlush(affectation);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Affectation en conflit : actualisez la liste avant de réessayer");
        }
        return convertirEnResponse(affectation, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AffectationResponse> consulterToutes(Long parcelleId) {
        autorisationService.verifierAdministrateur();
        List<Affectation> affectations;
        if (parcelleId == null) {
            affectations = affectationRepository.findAllByOrderByIdDesc();
        } else {
            if (!parcelleRepository.existsById(parcelleId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable");
            }
            affectations = affectationRepository.findByParcelle_IdOrderByIdDesc(parcelleId);
        }
        List<AffectationResponse> responses = new ArrayList<>();
        for (Affectation affectation : affectations) {
            Role role = compteKeycloakService.consulterRole(affectation.getUtilisateur().getKeycloakId());
            responses.add(convertirEnResponse(affectation, role));
        }
        return responses;
    }

    @Override
    public void retirer(Long id) {
        autorisationService.verifierAdministrateur();
        Optional<Affectation> affectation = affectationRepository.findById(id);
        if (affectation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Affectation introuvable");
        }
        affectationRepository.delete(affectation.get());
    }

    private AffectationResponse convertirEnResponse(Affectation affectation, Role role) {
        AffectationResponse response = new AffectationResponse();
        response.setId(affectation.getId());
        response.setUtilisateurId(affectation.getUtilisateur().getId());
        response.setUtilisateurNom(affectation.getUtilisateur().getNom());
        response.setUtilisateurPrenom(affectation.getUtilisateur().getPrenom());
        response.setUtilisateurEmail(affectation.getUtilisateur().getEmail());
        response.setRole(role);
        response.setParcelleId(affectation.getParcelle().getId());
        response.setParcelleCode(affectation.getParcelle().getCode());
        response.setParcelleNom(affectation.getParcelle().getNom());
        response.setExploitationNom(affectation.getParcelle().getExploitation().getNom());
        return response;
    }
}
