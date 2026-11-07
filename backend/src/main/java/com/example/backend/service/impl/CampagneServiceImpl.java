package com.example.backend.service.impl;

import com.example.backend.dto.campagne.CampagneRequest;
import com.example.backend.dto.campagne.CampagneResponse;
import com.example.backend.dto.campagne.ParcelleCampagneResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Campagne;
import com.example.backend.entity.Plantation;
import com.example.backend.entity.Parcelle;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.repository.AffectationRepository;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.repository.CampagneRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.CampagneService;
import com.example.backend.repository.PlantationRepository;
import com.example.backend.repository.ObservationRepository;
import com.example.backend.repository.IrrigationRepository;
import com.example.backend.repository.FertilisationRepository;
import com.example.backend.repository.TraitementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CampagneServiceImpl implements CampagneService {
    private final CampagneRepository campagneRepository;
    private final PlantationRepository plantationRepository;
    private final ObservationRepository observationRepository;
    private final IrrigationRepository irrigationRepository;
    private final FertilisationRepository fertilisationRepository;
    private final TraitementRepository traitementRepository;
    private final AutorisationService autorisationService;
    private final ParcelleRepository parcelleRepository;
    private final AffectationRepository affectationRepository;

    @Override
    public CampagneResponse creer(CampagneRequest request) {
        autorisationService.verifierAdministrateur();
        verifierDates(request);
        String nom = request.getNom().trim();
        String nomNormalise = nom.toLowerCase(Locale.ROOT);
        if (campagneRepository.existsByNomNormalise(nomNormalise)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne existe déjà");
        }
        Campagne campagne = Campagne.builder()
                .nom(nom).nomNormalise(nomNormalise).description(request.getDescription())
                .dateDebut(request.getDateDebut()).dateFin(request.getDateFin())
                .statut(StatutCampagne.PLANIFIEE)
                .parcelles(rechercherParcelles(request.getParcelleIds())).build();
        return convertirEnResponse(enregistrer(campagne));
    }

    @Override
    public CampagneResponse modifier(Long id, CampagneRequest request) {
        autorisationService.verifierAdministrateur();
        Campagne campagne = rechercherCampagnePourModification(id);
        if (campagne.getStatut() == StatutCampagne.CLOTUREE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une campagne clôturée ne peut plus être modifiée");
        }
        verifierDates(request);
        String nom = request.getNom().trim();
        String nomNormalise = nom.toLowerCase(Locale.ROOT);
        if (campagneRepository.existsByNomNormaliseAndIdNot(nomNormalise, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne existe déjà");
        }
        for (Plantation plantation : plantationRepository.findByCampagne_Id(id)) {
            if (!request.getParcelleIds().contains(plantation.getParcelle().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Une parcelle suivie par une plantation ne peut pas être retirée de la campagne");
            }
            if (plantation.getDatePlantation().isAfter(request.getDateFin())
                    || (plantation.getDateRecoltePrevue() != null
                    && (plantation.getDateRecoltePrevue().isBefore(request.getDateDebut())
                    || plantation.getDateRecoltePrevue().isAfter(request.getDateFin())))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ces dates sont incompatibles avec les plantations de la campagne");
            }
        }
        if (observationRepository.existsByPlantation_Campagne_IdAndDateObservationBefore(id, request.getDateDebut())
                || observationRepository.existsByPlantation_Campagne_IdAndDateObservationAfter(id, request.getDateFin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ces dates excluent une observation existante de la campagne");
        }
        if (irrigationRepository.existsByPlantation_Campagne_IdAndDateIrrigationBefore(id, request.getDateDebut())
                || irrigationRepository.existsByPlantation_Campagne_IdAndDateIrrigationAfter(id, request.getDateFin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ces dates excluent une irrigation existante de la campagne");
        }
        if (fertilisationRepository.existsByPlantation_Campagne_IdAndDateFertilisationBefore(id, request.getDateDebut())
                || fertilisationRepository.existsByPlantation_Campagne_IdAndDateFertilisationAfter(id, request.getDateFin())
                || traitementRepository.existsByPlantation_Campagne_IdAndDateTraitementBefore(id, request.getDateDebut())
                || traitementRepository.existsByPlantation_Campagne_IdAndDateTraitementAfter(id, request.getDateFin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ces dates excluent une fertilisation ou un traitement existant de la campagne");
        }
        campagne.setNom(nom);
        campagne.setNomNormalise(nomNormalise);
        campagne.setDescription(request.getDescription());
        campagne.setDateDebut(request.getDateDebut());
        campagne.setDateFin(request.getDateFin());
        campagne.setParcelles(rechercherParcelles(request.getParcelleIds()));
        return convertirEnResponse(enregistrer(campagne));
    }

    @Override
    public CampagneResponse changerStatut(Long id, StatutCampagne statut) {
        autorisationService.verifierAdministrateur();
        Campagne campagne = rechercherCampagnePourModification(id);
        StatutCampagne ancienStatut = campagne.getStatut();
        // Une nouvelle tentative du même changement reste sans effet supplémentaire.
        if (ancienStatut == statut) return convertirEnResponse(campagne);
        if (campagne.getParcelles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Associez au moins une parcelle avant de changer le statut");
        }
        boolean demarrage = ancienStatut == StatutCampagne.PLANIFIEE && statut == StatutCampagne.EN_COURS;
        boolean cloture = ancienStatut == StatutCampagne.EN_COURS && statut == StatutCampagne.CLOTUREE;
        if (!demarrage && !cloture) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transition impossible. Respectez l'ordre Planifiée, En cours, Clôturée");
        }
        campagne.setStatut(statut);
        return convertirEnResponse(enregistrer(campagne));
    }

    @Override
    @Transactional(readOnly = true)
    public CampagneResponse consulterParId(Long id) {
        autorisationService.verifierAccesCampagne(id);
        return convertirEnResponse(rechercherCampagne(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampagneResponse> consulterToutes(StatutCampagne statut) {
        autorisationService.obtenirRole();
        List<Campagne> campagnes;
        Sort tri = Sort.by("dateDebut").descending().and(Sort.by("id").descending());
        if (autorisationService.estAdministrateur()) {
            if (statut == null) campagnes = campagneRepository.findAll(tri);
            else campagnes = campagneRepository.findByStatut(statut, tri);
        } else {
            campagnes = campagneRepository.findAccessibles(autorisationService.obtenirKeycloakId(), statut, tri);
        }
        List<CampagneResponse> responses = new ArrayList<>();
        for (Campagne campagne : campagnes) responses.add(convertirEnResponse(campagne));
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CampagneResponse> consulterPage(Pageable pagination, StatutCampagne statut) {
        autorisationService.obtenirRole();
        Page<Campagne> resultat;
        if (autorisationService.estAdministrateur()) {
            if (statut == null) resultat = campagneRepository.findAll(pagination);
            else resultat = campagneRepository.findByStatut(statut, pagination);
        } else {
            resultat = campagneRepository.findPageAccessible(autorisationService.obtenirKeycloakId(), statut, pagination);
        }
        List<CampagneResponse> contenu = new ArrayList<>();
        for (Campagne campagne : resultat.getContent()) contenu.add(convertirEnResponse(campagne));
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    private void verifierDates(CampagneRequest request) {
        if (request.getDateDebut() == null || request.getDateFin() == null
                || !request.getDateFin().isAfter(request.getDateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de fin doit être postérieure à la date de début");
        }
    }

    @Override
    public CampagneResponse associerParcelles(Long id, List<Long> parcelleIds) {
        autorisationService.verifierAdministrateur();
        Campagne campagne = rechercherCampagnePourModification(id);
        if (!campagne.getParcelles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Les parcelles sont déjà définies. Utilisez la modification de la campagne");
        }
        // Rattachement initial des campagnes enregistrées avant cette fonctionnalité.
        campagne.setParcelles(rechercherParcelles(parcelleIds));
        return convertirEnResponse(enregistrer(campagne));
    }

    private List<Parcelle> rechercherParcelles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez au moins une parcelle");
        }
        List<Long> idsVus = new ArrayList<>();
        List<Parcelle> parcelles = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0 || idsVus.contains(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sélection des parcelles est invalide");
            }
            Optional<Parcelle> resultat = parcelleRepository.findById(id);
            if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable");
            idsVus.add(id);
            parcelles.add(resultat.get());
        }
        return parcelles;
    }

    private Campagne rechercherCampagnePourModification(Long id) {
        Optional<Campagne> resultat = campagneRepository.findPourModification(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable");
        return resultat.get();
    }

    private Campagne rechercherCampagne(Long id) {
        Optional<Campagne> resultat = campagneRepository.findById(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable");
        return resultat.get();
    }

    private Campagne enregistrer(Campagne campagne) {
        try {
            return campagneRepository.saveAndFlush(campagne);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La campagne a été modifiée par une autre personne. Actualisez avant de réessayer");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne existe déjà");
        }
    }

    private CampagneResponse convertirEnResponse(Campagne campagne) {
        boolean administrateur = autorisationService.estAdministrateur();
        List<Long> ids = new ArrayList<>();
        List<ParcelleCampagneResponse> parcelles = new ArrayList<>();
        for (Parcelle parcelle : campagne.getParcelles()) {
            if (administrateur || affectationRepository.existsByUtilisateur_KeycloakIdAndParcelle_Id(
                    autorisationService.obtenirKeycloakId(), parcelle.getId())) {
                ParcelleCampagneResponse detail = new ParcelleCampagneResponse();
                detail.setId(parcelle.getId());
                detail.setCode(parcelle.getCode());
                detail.setNom(parcelle.getNom());
                detail.setExploitationId(parcelle.getExploitation().getId());
                detail.setExploitationNom(parcelle.getExploitation().getNom());
                parcelles.add(detail);
                ids.add(parcelle.getId());
            }
        }
        if (!administrateur && parcelles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès à cette campagne refusé");
        }
        CampagneResponse response = new CampagneResponse();
        response.setId(campagne.getId());
        response.setNom(campagne.getNom());
        response.setDescription(campagne.getDescription());
        response.setDateDebut(campagne.getDateDebut());
        response.setDateFin(campagne.getDateFin());
        response.setStatut(campagne.getStatut());
        response.setParcelleIds(ids);
        response.setParcelles(parcelles);
        return response;
    }
}
