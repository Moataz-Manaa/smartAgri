package com.example.backend.service.impl;

import com.example.backend.dto.irrigation.IrrigationRequest;
import com.example.backend.dto.irrigation.IrrigationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Campagne;
import com.example.backend.entity.Irrigation;
import com.example.backend.entity.Plantation;
import com.example.backend.entity.Utilisateur;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.repository.CampagneRepository;
import com.example.backend.repository.IrrigationRepository;
import com.example.backend.repository.PlantationRepository;
import com.example.backend.repository.UtilisateurRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.IrrigationService;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import com.example.backend.enums.UniteVolume;
import com.example.backend.dto.irrigation.TotalIrrigationResponse;
import jakarta.persistence.EntityManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class IrrigationServiceImpl implements IrrigationService {
    private final IrrigationRepository irrigationRepository;
    private final PlantationRepository plantationRepository;
    private final CampagneRepository campagneRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AutorisationService autorisationService;
    private final EntityManager entityManager;

    @Override
    public IrrigationResponse creer(IrrigationRequest request) {
        autorisationService.verifierAgriculteur();
        Plantation plantation = rechercherPlantation(request.getPlantationId());
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        Optional<Utilisateur> auteur = utilisateurRepository.findByKeycloakId(autorisationService.obtenirKeycloakId());
        if (auteur.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profil métier introuvable");
        Irrigation irrigation = Irrigation.builder().plantation(plantation).auteur(auteur.get())
                .build();
        appliquerDonnees(irrigation, request);
        return convertirEnResponse(enregistrer(irrigation));
    }

    @Override
    public IrrigationResponse modifier(Long id, IrrigationRequest request) {
        autorisationService.verifierAgriculteur();
        Irrigation irrigation = rechercherIrrigation(id);
        Plantation plantation = irrigation.getPlantation();
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        if (!plantation.getId().equals(request.getPlantationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La plantation d'une irrigation ne peut pas être changée");
        }
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        appliquerDonnees(irrigation, request);
        return convertirEnResponse(enregistrer(irrigation));
    }

    @Override
    @Transactional(readOnly = true)
    public IrrigationResponse consulterParId(Long id) {
        Irrigation irrigation = rechercherIrrigation(id);
        autorisationService.verifierAccesParcelle(irrigation.getPlantation().getParcelle().getId());
        return convertirEnResponse(irrigation);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IrrigationResponse> consulterPage(Long plantationId, Pageable pagination) {
        Plantation plantation = rechercherPlantation(plantationId);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Page<Irrigation> resultat = irrigationRepository.findByPlantation_Id(plantationId, pagination);
        List<IrrigationResponse> contenu = new ArrayList<>();
        for (Irrigation irrigation : resultat.getContent()) contenu.add(convertirEnResponse(irrigation));
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public TotalIrrigationResponse consulterTotal(Long plantationId) {
        Plantation plantation = rechercherPlantation(plantationId);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        return new TotalIrrigationResponse(irrigationRepository.calculerTotalMetresCubes(plantationId));
    }

    private void appliquerDonnees(Irrigation irrigation, IrrigationRequest request) {
        irrigation.setDateIrrigation(request.getDateIrrigation());
        irrigation.setVolume(request.getVolume());
        irrigation.setUnite(request.getUnite());
        BigDecimal volumeMetresCubes = request.getVolume();
        if (request.getUnite() == UniteVolume.LITRE) volumeMetresCubes = volumeMetresCubes.movePointLeft(3);
        irrigation.setVolumeMetresCubes(volumeMetresCubes);
        irrigation.setCommentaire(request.getCommentaire() == null ? null : request.getCommentaire().trim());
    }

    private void verifierDonnees(IrrigationRequest request, Plantation plantation, Campagne campagne) {
        LocalDate date = request.getDateIrrigation();
        if (date.isAfter(LocalDate.now()) || date.isBefore(plantation.getDatePlantation())
                || date.isBefore(campagne.getDateDebut()) || date.isAfter(campagne.getDateFin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date doit appartenir à la campagne, suivre la plantation et ne pas être future");
        }
    }

    private Campagne verrouillerCampagneOuverte(Long id) {
        Optional<Campagne> resultat = campagneRepository.findPourModification(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable");
        if (resultat.get().getStatut() == StatutCampagne.CLOTUREE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne est clôturée");
        }
        return resultat.get();
    }

    private Plantation rechercherPlantation(Long id) {
        Optional<Plantation> resultat = plantationRepository.findById(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantation introuvable");
        return resultat.get();
    }

    private Irrigation rechercherIrrigation(Long id) {
        Optional<Irrigation> resultat = irrigationRepository.findById(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Irrigation introuvable");
        return resultat.get();
    }

    private Irrigation enregistrer(Irrigation irrigation) {
        try {
            return irrigationRepository.saveAndFlush(irrigation);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "L'irrigation a été modifiée. Rouvrez la fiche avant de réessayer");
        }
    }

    private IrrigationResponse convertirEnResponse(Irrigation irrigation) {
        IrrigationResponse response = new IrrigationResponse();
        response.setId(irrigation.getId());
        response.setPlantationId(irrigation.getPlantation().getId());
        response.setDateIrrigation(irrigation.getDateIrrigation());
        response.setVolume(irrigation.getVolume());
        response.setUnite(irrigation.getUnite());
        response.setVolumeMetresCubes(irrigation.getVolumeMetresCubes());
        response.setCommentaire(irrigation.getCommentaire());
        response.setAuteurNom(irrigation.getAuteur().getNom());
        response.setAuteurPrenom(irrigation.getAuteur().getPrenom());
        response.setCampagneCloturee(irrigation.getPlantation().getCampagne().getStatut() == StatutCampagne.CLOTUREE);
        return response;
    }
}
