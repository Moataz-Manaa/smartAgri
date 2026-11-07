package com.example.backend.service.impl;

import com.example.backend.dto.fertilisation.FertilisationRequest;
import com.example.backend.dto.fertilisation.FertilisationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Campagne;
import com.example.backend.entity.Fertilisation;
import com.example.backend.entity.Plantation;
import com.example.backend.entity.Utilisateur;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.repository.CampagneRepository;
import com.example.backend.repository.FertilisationRepository;
import com.example.backend.repository.PlantationRepository;
import com.example.backend.repository.UtilisateurRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.FertilisationService;
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
public class FertilisationServiceImpl implements FertilisationService {
    private final FertilisationRepository fertilisationRepository;
    private final PlantationRepository plantationRepository;
    private final CampagneRepository campagneRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AutorisationService autorisationService;
    private final EntityManager entityManager;

    @Override
    public FertilisationResponse creer(FertilisationRequest request) {
        autorisationService.verifierAgriculteur();
        Plantation plantation = rechercherPlantation(request.getPlantationId());
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        Optional<Utilisateur> auteur = utilisateurRepository.findByKeycloakId(autorisationService.obtenirKeycloakId());
        if (auteur.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profil métier introuvable");
        Fertilisation fertilisation = Fertilisation.builder().plantation(plantation).auteur(auteur.get())
                .build();
        appliquerDonnees(fertilisation, request);
        return convertirEnResponse(enregistrer(fertilisation));
    }

    @Override
    public FertilisationResponse modifier(Long id, FertilisationRequest request) {
        autorisationService.verifierAgriculteur();
        Fertilisation fertilisation = rechercherFertilisation(id);
        Plantation plantation = fertilisation.getPlantation();
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        if (!plantation.getId().equals(request.getPlantationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La plantation d'une fertilisation ne peut pas être changée");
        }
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        appliquerDonnees(fertilisation, request);
        return convertirEnResponse(enregistrer(fertilisation));
    }

    @Override
    @Transactional(readOnly = true)
    public FertilisationResponse consulterParId(Long id) {
        Fertilisation fertilisation = rechercherFertilisation(id);
        autorisationService.verifierAccesParcelle(fertilisation.getPlantation().getParcelle().getId());
        return convertirEnResponse(fertilisation);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FertilisationResponse> consulterPage(Long plantationId, Pageable pagination) {
        Plantation plantation = rechercherPlantation(plantationId);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Page<Fertilisation> resultat = fertilisationRepository.findByPlantation_Id(plantationId, pagination);
        List<FertilisationResponse> contenu = new ArrayList<>();
        for (Fertilisation fertilisation : resultat.getContent()) contenu.add(convertirEnResponse(fertilisation));
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    private void appliquerDonnees(Fertilisation fertilisation, FertilisationRequest request) {
        fertilisation.setDateFertilisation(request.getDateFertilisation());
        fertilisation.setNomProduit(request.getNomProduit().trim());
        fertilisation.setQuantite(request.getQuantite());
        fertilisation.setUnite(request.getUnite());
        fertilisation.setMethodeApplication(request.getMethodeApplication() == null ? null : request.getMethodeApplication().trim());
        fertilisation.setCommentaire(request.getCommentaire() == null ? null : request.getCommentaire().trim());
        fertilisation.setTypeEngrais(request.getTypeEngrais() == null ? null : request.getTypeEngrais().trim());
    }

    private void verifierDonnees(FertilisationRequest request, Plantation plantation, Campagne campagne) {
        LocalDate date = request.getDateFertilisation();
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

    private Fertilisation rechercherFertilisation(Long id) {
        Optional<Fertilisation> resultat = fertilisationRepository.findById(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fertilisation introuvable");
        return resultat.get();
    }

    private Fertilisation enregistrer(Fertilisation fertilisation) {
        try {
            return fertilisationRepository.saveAndFlush(fertilisation);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La fertilisation a été modifiée. Rouvrez la fiche avant de réessayer");
        }
    }

    private FertilisationResponse convertirEnResponse(Fertilisation fertilisation) {
        FertilisationResponse response = new FertilisationResponse();
        response.setId(fertilisation.getId());
        response.setPlantationId(fertilisation.getPlantation().getId());
        response.setDateFertilisation(fertilisation.getDateFertilisation());
        response.setNomProduit(fertilisation.getNomProduit());
        response.setQuantite(fertilisation.getQuantite());
        response.setUnite(fertilisation.getUnite());
        response.setMethodeApplication(fertilisation.getMethodeApplication());
        response.setCommentaire(fertilisation.getCommentaire());
        response.setTypeEngrais(fertilisation.getTypeEngrais());
        response.setAuteurNom(fertilisation.getAuteur().getNom());
        response.setAuteurPrenom(fertilisation.getAuteur().getPrenom());
        response.setCampagneCloturee(fertilisation.getPlantation().getCampagne().getStatut() == StatutCampagne.CLOTUREE);
        return response;
    }
}
