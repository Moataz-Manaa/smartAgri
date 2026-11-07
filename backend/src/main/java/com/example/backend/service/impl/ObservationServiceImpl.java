package com.example.backend.service.impl;

import com.example.backend.dto.observation.ObservationRequest;
import com.example.backend.dto.observation.ObservationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Campagne;
import com.example.backend.entity.Observation;
import com.example.backend.entity.Plantation;
import com.example.backend.entity.Utilisateur;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.repository.CampagneRepository;
import com.example.backend.repository.ObservationRepository;
import com.example.backend.repository.PlantationRepository;
import com.example.backend.repository.UtilisateurRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.ObservationService;
import lombok.RequiredArgsConstructor;
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
public class ObservationServiceImpl implements ObservationService {
    private final ObservationRepository observationRepository;
    private final PlantationRepository plantationRepository;
    private final CampagneRepository campagneRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AutorisationService autorisationService;
    private final EntityManager entityManager;

    @Override
    public ObservationResponse creer(ObservationRequest request) {
        autorisationService.verifierAgriculteur();
        Plantation plantation = rechercherPlantation(request.getPlantationId());
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        Optional<Utilisateur> auteur = utilisateurRepository.findByKeycloakId(autorisationService.obtenirKeycloakId());
        if (auteur.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profil métier introuvable");
        Observation observation = Observation.builder().plantation(plantation).auteur(auteur.get())
                .dateObservation(request.getDateObservation()).description(request.getDescription().trim()).build();
        return convertirEnResponse(enregistrer(observation));
    }

    @Override
    public ObservationResponse modifier(Long id, ObservationRequest request) {
        autorisationService.verifierAgriculteur();
        Observation observation = rechercherObservation(id);
        Plantation plantation = observation.getPlantation();
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        if (!plantation.getId().equals(request.getPlantationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La plantation d'une observation ne peut pas être changée");
        }
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        observation.setDateObservation(request.getDateObservation());
        observation.setDescription(request.getDescription().trim());
        return convertirEnResponse(enregistrer(observation));
    }

    @Override
    @Transactional(readOnly = true)
    public ObservationResponse consulterParId(Long id) {
        Observation observation = rechercherObservation(id);
        autorisationService.verifierAccesParcelle(observation.getPlantation().getParcelle().getId());
        return convertirEnResponse(observation);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ObservationResponse> consulterPage(Long plantationId, Pageable pagination) {
        Plantation plantation = rechercherPlantation(plantationId);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Page<Observation> resultat = observationRepository.findByPlantation_Id(plantationId, pagination);
        List<ObservationResponse> contenu = new ArrayList<>();
        for (Observation observation : resultat.getContent()) contenu.add(convertirEnResponse(observation));
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    private void verifierDonnees(ObservationRequest request, Plantation plantation, Campagne campagne) {
        LocalDate date = request.getDateObservation();
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

    private Observation rechercherObservation(Long id) {
        Optional<Observation> resultat = observationRepository.findById(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Observation introuvable");
        return resultat.get();
    }

    private Observation enregistrer(Observation observation) {
        try {
            return observationRepository.saveAndFlush(observation);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "L'observation a été modifiée. Rouvrez la fiche avant de réessayer");
        }
    }

    private ObservationResponse convertirEnResponse(Observation observation) {
        ObservationResponse response = new ObservationResponse();
        response.setId(observation.getId());
        response.setPlantationId(observation.getPlantation().getId());
        response.setDateObservation(observation.getDateObservation());
        response.setDescription(observation.getDescription());
        response.setAuteurNom(observation.getAuteur().getNom());
        response.setAuteurPrenom(observation.getAuteur().getPrenom());
        response.setCampagneCloturee(observation.getPlantation().getCampagne().getStatut() == StatutCampagne.CLOTUREE);
        return response;
    }
}
