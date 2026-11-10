package com.example.backend.service.impl;

import com.example.backend.dto.recolte.RecolteRequest;
import com.example.backend.dto.recolte.RecolteResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Campagne;
import com.example.backend.entity.Recolte;
import com.example.backend.entity.Plantation;
import com.example.backend.entity.Utilisateur;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.repository.CampagneRepository;
import com.example.backend.repository.RecolteRepository;
import com.example.backend.repository.PlantationRepository;
import com.example.backend.repository.UtilisateurRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.RecolteService;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.example.backend.enums.UniteRecolte;
import com.example.backend.dto.recolte.TotalRecolteResponse;
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
public class RecolteServiceImpl implements RecolteService {
    private final RecolteRepository recolteRepository;
    private final PlantationRepository plantationRepository;
    private final CampagneRepository campagneRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AutorisationService autorisationService;
    private final EntityManager entityManager;

    @Override
    public RecolteResponse creer(RecolteRequest request) {
        autorisationService.verifierAgriculteur();
        Plantation plantation = rechercherPlantation(request.getPlantationId());
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        Optional<Utilisateur> auteur = utilisateurRepository.findByKeycloakId(autorisationService.obtenirKeycloakId());
        if (auteur.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profil métier introuvable");
        Recolte recolte = Recolte.builder().plantation(plantation).auteur(auteur.get())
                .build();
        appliquerDonnees(recolte, request);
        return convertirEnResponse(enregistrer(recolte));
    }

    @Override
    public RecolteResponse modifier(Long id, RecolteRequest request) {
        autorisationService.verifierAgriculteur();
        Recolte recolte = rechercherRecolte(id);
        Plantation plantation = recolte.getPlantation();
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        if (!plantation.getId().equals(request.getPlantationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La plantation d'une récolte ne peut pas être changée");
        }
        Campagne campagne = verrouillerCampagneOuverte(plantation.getCampagne().getId());
        entityManager.refresh(plantation);
        verifierDonnees(request, plantation, campagne);
        appliquerDonnees(recolte, request);
        return convertirEnResponse(enregistrer(recolte));
    }

    @Override
    @Transactional(readOnly = true)
    public RecolteResponse consulterParId(Long id) {
        Recolte recolte = rechercherRecolte(id);
        autorisationService.verifierAccesParcelle(recolte.getPlantation().getParcelle().getId());
        return convertirEnResponse(recolte);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RecolteResponse> consulterPage(Long plantationId, Pageable pagination) {
        Plantation plantation = rechercherPlantation(plantationId);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        Page<Recolte> resultat = recolteRepository.findByPlantation_Id(plantationId, pagination);
        List<RecolteResponse> contenu = new ArrayList<>();
        for (Recolte recolte : resultat.getContent()) contenu.add(convertirEnResponse(recolte));
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public TotalRecolteResponse consulterTotal(Long plantationId) {
        Plantation plantation = rechercherPlantation(plantationId);
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        BigDecimal quantiteTotale = recolteRepository.calculerTotalKilogrammes(plantationId);
        Double superficie = plantation.getSuperficie();
        BigDecimal rendement = null;
        if (quantiteTotale.signum() > 0 && superficie != null && Double.isFinite(superficie) && superficie > 0) {
            rendement = quantiteTotale.divide(BigDecimal.valueOf(superficie), 3, RoundingMode.HALF_UP);
        }
        return new TotalRecolteResponse(quantiteTotale, superficie, rendement);
    }

    private void appliquerDonnees(Recolte recolte, RecolteRequest request) {
        recolte.setDateRecolte(request.getDateRecolte());
        recolte.setQuantite(request.getQuantite());
        recolte.setUnite(request.getUnite());
        BigDecimal quantiteKilogrammes = request.getQuantite();
        if (request.getUnite() == UniteRecolte.TONNE) quantiteKilogrammes = quantiteKilogrammes.movePointRight(3);
        recolte.setQuantiteKilogrammes(quantiteKilogrammes);
        recolte.setCommentaire(request.getCommentaire() == null ? null : request.getCommentaire().trim());
    }

    private void verifierDonnees(RecolteRequest request, Plantation plantation, Campagne campagne) {
        LocalDate date = request.getDateRecolte();
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

    private Recolte rechercherRecolte(Long id) {
        Optional<Recolte> resultat = recolteRepository.findById(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Récolte introuvable");
        return resultat.get();
    }

    private Recolte enregistrer(Recolte recolte) {
        try {
            return recolteRepository.saveAndFlush(recolte);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La récolte a été modifiée. Rouvrez la fiche avant de réessayer");
        }
    }

    private RecolteResponse convertirEnResponse(Recolte recolte) {
        RecolteResponse response = new RecolteResponse();
        response.setId(recolte.getId());
        response.setPlantationId(recolte.getPlantation().getId());
        response.setDateRecolte(recolte.getDateRecolte());
        response.setQuantite(recolte.getQuantite());
        response.setUnite(recolte.getUnite());
        response.setQuantiteKilogrammes(recolte.getQuantiteKilogrammes());
        response.setCommentaire(recolte.getCommentaire());
        response.setAuteurNom(recolte.getAuteur().getNom());
        response.setAuteurPrenom(recolte.getAuteur().getPrenom());
        response.setCampagneCloturee(recolte.getPlantation().getCampagne().getStatut() == StatutCampagne.CLOTUREE);
        return response;
    }
}
