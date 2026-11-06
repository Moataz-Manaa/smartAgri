package com.example.backend.service.impl;

import com.example.backend.dto.plantation.PlantationRequest;
import com.example.backend.dto.plantation.PlantationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.*;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.enums.StatutPlantation;
import com.example.backend.repository.*;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.PlantationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.OptimisticLockingFailureException;
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
public class PlantationServiceImpl implements PlantationService {
    private final PlantationRepository plantationRepository;
    private final CampagneRepository campagneRepository;
    private final ParcelleRepository parcelleRepository;
    private final VarieteRepository varieteRepository;
    private final AutorisationService autorisationService;

    @Override
    public PlantationResponse creer(PlantationRequest request) {
        autorisationService.verifierAgriculteur();
        autorisationService.verifierAccesParcelle(request.getParcelleId());
        Campagne campagne = rechercherCampagneOuverte(request.getCampagneId());
        Parcelle parcelle = rechercherParcelle(request.getParcelleId());
        Variete variete = rechercherVariete(request.getVarieteId());
        verifierDonnees(request, campagne, parcelle);
        Plantation plantation = Plantation.builder().campagne(campagne).parcelle(parcelle).variete(variete).build();
        appliquerDonnees(plantation, request);
        return convertirEnResponse(enregistrer(plantation));
    }

    @Override
    public PlantationResponse modifier(Long id, PlantationRequest request) {
        autorisationService.verifierAgriculteur();
        Plantation plantation = rechercherPlantation(id);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        if (!plantation.getParcelle().getId().equals(request.getParcelleId())
                || !plantation.getCampagne().getId().equals(request.getCampagneId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La parcelle et la campagne d'une fiche ne peuvent pas être changées");
        }
        Campagne campagne = rechercherCampagneOuverte(request.getCampagneId());
        Parcelle parcelle = rechercherParcelle(request.getParcelleId());
        Variete variete = rechercherVariete(request.getVarieteId());
        verifierDonnees(request, campagne, parcelle);
        plantation.setVariete(variete);
        appliquerDonnees(plantation, request);
        return convertirEnResponse(enregistrer(plantation));
    }

    @Override
    @Transactional(readOnly = true)
    public PlantationResponse consulterParId(Long id) {
        Plantation plantation = rechercherPlantation(id);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        return convertirEnResponse(plantation);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlantationResponse> consulterPage(Pageable pagination, Long parcelleId,
            Long campagneId, StatutPlantation statut) {
        autorisationService.obtenirRole();
        if (parcelleId != null) autorisationService.verifierAccesParcelle(parcelleId);
        if (campagneId != null) autorisationService.verifierAccesCampagne(campagneId);
        Page<Plantation> resultat = plantationRepository.findPageAccessible(parcelleId, campagneId, statut,
                autorisationService.estAdministrateur(), autorisationService.obtenirKeycloakId(), pagination);
        List<PlantationResponse> contenu = new ArrayList<>();
        for (Plantation plantation : resultat.getContent()) contenu.add(convertirEnResponse(plantation));
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    private void verifierDonnees(PlantationRequest request, Campagne campagne, Parcelle parcelle) {
        boolean rattachee = false;
        for (Parcelle element : campagne.getParcelles()) {
            if (element.getId().equals(parcelle.getId())) rattachee = true;
        }
        if (!rattachee) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette parcelle ne participe pas à la campagne");
        }
        if (request.getSuperficie() == null || !Double.isFinite(request.getSuperficie())
                || request.getSuperficie() <= 0 || request.getSuperficie() > parcelle.getSuperficie()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La superficie doit être positive et ne pas dépasser celle de la parcelle");
        }
        // Une plantation pérenne peut être plus ancienne que la campagne suivie.
        if (request.getDatePlantation().isAfter(campagne.getDateFin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de plantation ne peut pas dépasser la fin de la campagne");
        }
        if (request.getDateRecoltePrevue() != null
                && (request.getDateRecoltePrevue().isBefore(request.getDatePlantation())
                || request.getDateRecoltePrevue().isBefore(campagne.getDateDebut())
                || request.getDateRecoltePrevue().isAfter(campagne.getDateFin()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La récolte prévue doit suivre la plantation et appartenir à la campagne");
        }
    }

    private void appliquerDonnees(Plantation plantation, PlantationRequest request) {
        plantation.setDatePlantation(request.getDatePlantation());
        plantation.setSuperficie(request.getSuperficie());
        plantation.setQuantitePlantee(request.getQuantitePlantee());
        plantation.setDateRecoltePrevue(request.getDateRecoltePrevue());
        plantation.setCommentaire(request.getCommentaire());
        plantation.setStatut(request.getStatut());
    }

    private Plantation rechercherPlantation(Long id) {
        Optional<Plantation> resultat = plantationRepository.findById(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantation introuvable");
        return resultat.get();
    }

    private Plantation enregistrer(Plantation plantation) {
        try {
            return plantationRepository.saveAndFlush(plantation);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La fiche a été modifiée par une autre personne. Actualisez avant de réessayer");
        }
    }

    private Campagne rechercherCampagneOuverte(Long id) {
        // Le même verrou est utilisé par l'administration pour modifier ou clôturer la campagne.
        Optional<Campagne> resultat = campagneRepository.findPourModification(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable");
        Campagne campagne = resultat.get();
        if (campagne.getStatut() == StatutCampagne.CLOTUREE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne est clôturée");
        }
        return campagne;
    }

    private Parcelle rechercherParcelle(Long id) {
        Optional<Parcelle> resultat = parcelleRepository.findPourModification(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable");
        return resultat.get();
    }

    private Variete rechercherVariete(Long id) {
        Optional<Variete> resultat = varieteRepository.findPourModification(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variété introuvable");
        return resultat.get();
    }

    private PlantationResponse convertirEnResponse(Plantation plantation) {
        PlantationResponse response = new PlantationResponse();
        response.setId(plantation.getId());
        response.setParcelleId(plantation.getParcelle().getId());
        response.setParcelleCode(plantation.getParcelle().getCode());
        response.setParcelleNom(plantation.getParcelle().getNom());
        response.setCampagneId(plantation.getCampagne().getId());
        response.setCampagneNom(plantation.getCampagne().getNom());
        response.setCampagneCloturee(plantation.getCampagne().getStatut() == StatutCampagne.CLOTUREE);
        response.setVarieteId(plantation.getVariete().getId());
        response.setVarieteNom(plantation.getVariete().getNom());
        response.setCultureId(plantation.getVariete().getCulture().getId());
        response.setCultureNom(plantation.getVariete().getCulture().getNom());
        response.setDatePlantation(plantation.getDatePlantation());
        response.setSuperficie(plantation.getSuperficie());
        response.setQuantitePlantee(plantation.getQuantitePlantee());
        response.setDateRecoltePrevue(plantation.getDateRecoltePrevue());
        response.setCommentaire(plantation.getCommentaire());
        response.setStatut(plantation.getStatut());
        return response;
    }
}
