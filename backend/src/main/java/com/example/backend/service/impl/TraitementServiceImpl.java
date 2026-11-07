package com.example.backend.service.impl;

import com.example.backend.dto.traitement.TraitementRequest;
import com.example.backend.dto.traitement.TraitementResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Campagne;
import com.example.backend.entity.Traitement;
import com.example.backend.entity.Plantation;
import com.example.backend.entity.Utilisateur;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.repository.CampagneRepository;
import com.example.backend.repository.TraitementRepository;
import com.example.backend.repository.PlantationRepository;
import com.example.backend.repository.UtilisateurRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.TraitementService;
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
public class TraitementServiceImpl implements TraitementService {
    private final TraitementRepository traitementRepository;
    private final PlantationRepository plantationRepository;
    private final CampagneRepository campagneRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AutorisationService autorisationService;
    private final EntityManager entityManager;

    @Override
    public TraitementResponse creer(TraitementRequest request) {
        autorisationService.verifierAgriculteur();
        Plantation plantation = rechercherPlantation(request.getPlantationId());
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        Optional<Utilisateur> auteur = utilisateurRepository.findByKeycloakId(autorisationService.obtenirKeycloakId());
        if (auteur.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profil métier introuvable");
        Traitement traitement = Traitement.builder().plantation(plantation).auteur(auteur.get())
                .build();
        appliquerDonnees(traitement, request);
        return convertirEnResponse(enregistrer(traitement));
    }

    @Override
    public TraitementResponse modifier(Long id, TraitementRequest request) {
        autorisationService.verifierAgriculteur();
        Traitement traitement = rechercherTraitement(id);
        Plantation plantation = traitement.getPlantation();
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        if (!plantation.getId().equals(request.getPlantationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La plantation d'un traitement ne peut pas être changée");
        }
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        appliquerDonnees(traitement, request);
        return convertirEnResponse(enregistrer(traitement));
    }

    @Override
    @Transactional(readOnly = true)
    public TraitementResponse consulterParId(Long id) {
        Traitement traitement = rechercherTraitement(id);
        autorisationService.verifierAccesParcelle(traitement.getPlantation().getParcelle().getId());
        return convertirEnResponse(traitement);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TraitementResponse> consulterPage(Long plantationId, Pageable pagination) {
        Plantation plantation = rechercherPlantation(plantationId);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Page<Traitement> resultat = traitementRepository.findByPlantation_Id(plantationId, pagination);
        List<TraitementResponse> contenu = new ArrayList<>();
        for (Traitement traitement : resultat.getContent()) contenu.add(convertirEnResponse(traitement));
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    private void appliquerDonnees(Traitement traitement, TraitementRequest request) {
        traitement.setDateTraitement(request.getDateTraitement());
        traitement.setNomProduit(request.getNomProduit().trim());
        traitement.setQuantite(request.getQuantite());
        traitement.setUnite(request.getUnite());
        traitement.setMethodeApplication(request.getMethodeApplication() == null ? null : request.getMethodeApplication().trim());
        traitement.setCommentaire(request.getCommentaire() == null ? null : request.getCommentaire().trim());
        traitement.setCible(request.getCible().trim());
    }

    private void verifierDonnees(TraitementRequest request, Plantation plantation, Campagne campagne) {
        LocalDate date = request.getDateTraitement();
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

    private Traitement rechercherTraitement(Long id) {
        Optional<Traitement> resultat = traitementRepository.findById(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Traitement introuvable");
        return resultat.get();
    }

    private Traitement enregistrer(Traitement traitement) {
        try {
            return traitementRepository.saveAndFlush(traitement);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le traitement a été modifiée. Rouvrez la fiche avant de réessayer");
        }
    }

    private TraitementResponse convertirEnResponse(Traitement traitement) {
        TraitementResponse response = new TraitementResponse();
        response.setId(traitement.getId());
        response.setPlantationId(traitement.getPlantation().getId());
        response.setDateTraitement(traitement.getDateTraitement());
        response.setNomProduit(traitement.getNomProduit());
        response.setQuantite(traitement.getQuantite());
        response.setUnite(traitement.getUnite());
        response.setMethodeApplication(traitement.getMethodeApplication());
        response.setCommentaire(traitement.getCommentaire());
        response.setCible(traitement.getCible());
        response.setAuteurNom(traitement.getAuteur().getNom());
        response.setAuteurPrenom(traitement.getAuteur().getPrenom());
        response.setCampagneCloturee(traitement.getPlantation().getCampagne().getStatut() == StatutCampagne.CLOTUREE);
        return response;
    }
}
