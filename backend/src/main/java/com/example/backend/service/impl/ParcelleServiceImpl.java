package com.example.backend.service.impl;

import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.parcelle.ParcelleRequest;
import com.example.backend.dto.parcelle.ParcelleResponse;
import com.example.backend.entity.Exploitation;
import com.example.backend.entity.Parcelle;
import com.example.backend.repository.ExploitationRepository;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.ParcelleService;
import com.example.backend.repository.PlantationRepository;
import lombok.RequiredArgsConstructor;
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
public class ParcelleServiceImpl implements ParcelleService {

    private final ParcelleRepository parcelleRepository;
    private final PlantationRepository plantationRepository;

    private final ExploitationRepository exploitationRepository;
    private final AutorisationService autorisationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ParcelleResponse> consulterPage(Pageable pagination) {
        
        Page<Parcelle> resultat;
        if (autorisationService.estAdministrateur()) {
            resultat = parcelleRepository.findAll(pagination);
        } else {
            resultat = parcelleRepository.findPageAccessible(
                    autorisationService.obtenirKeycloakId(), pagination);
        }
        List<ParcelleResponse> contenu = new ArrayList<>();
        for (Parcelle element : resultat.getContent()) {
            
            contenu.add(convertirEnResponse(element));
        }
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    @Override
    public ParcelleResponse creer(ParcelleRequest request) {
        autorisationService.verifierAdministrateur();
        Exploitation exploitation =
                rechercherExploitation(request.getExploitationId());

        Long numero = parcelleRepository.genererNumeroCode();
        String code = String.format("PAR-%02d", numero);

        Parcelle parcelle = Parcelle.builder()
                .code(code)
                .nom(request.getNom())
                .superficie(request.getSuperficie())
                .typeSol(request.getTypeSol())
                .typeIrrigation(request.getTypeIrrigation())
                .description(request.getDescription())
                .exploitation(exploitation)
                .build();

        Parcelle parcelleEnregistree = parcelleRepository.save(parcelle);

        return convertirEnResponse(parcelleEnregistree);
    }

    @Override
    public ParcelleResponse modifier(Long id, ParcelleRequest request) {
        autorisationService.verifierAdministrateur();
        Optional<Parcelle> resultat = parcelleRepository.findPourModification(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable");
        Parcelle parcelle = resultat.get();
        if (plantationRepository.existsByParcelle_IdAndSuperficieGreaterThan(id, request.getSuperficie())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La superficie ne peut pas être inférieure à celle d'une plantation existante");
        }

        Exploitation exploitation =
                rechercherExploitation(request.getExploitationId());

        parcelle.setNom(request.getNom());
        parcelle.setSuperficie(request.getSuperficie());
        parcelle.setTypeSol(request.getTypeSol());
        parcelle.setTypeIrrigation(request.getTypeIrrigation());
        parcelle.setDescription(request.getDescription());
        parcelle.setExploitation(exploitation);

        Parcelle parcelleModifiee = parcelleRepository.save(parcelle);

        return convertirEnResponse(parcelleModifiee);
    }

    @Override
    @Transactional(readOnly = true)
    public ParcelleResponse consulterParId(Long id) {
        autorisationService.verifierAccesParcelle(id);
        Parcelle parcelle = rechercherParcelle(id);

        return convertirEnResponse(parcelle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParcelleResponse> consulterToutes() {
        List<Parcelle> parcelles;
        if (autorisationService.estAdministrateur()) {
            parcelles = parcelleRepository.findAll();
        } else {
            parcelles = parcelleRepository.findAccessibles(autorisationService.obtenirKeycloakId());
        }

        return convertirEnListeResponse(parcelles);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParcelleResponse> consulterParExploitation(
            Long exploitationId) {

        autorisationService.verifierAccesExploitation(exploitationId);
        rechercherExploitation(exploitationId);

        List<Parcelle> parcelles;
        if (autorisationService.estAdministrateur()) {
            parcelles = parcelleRepository.findByExploitation_Id(exploitationId);
        } else {
            parcelles = parcelleRepository.findAccessiblesParExploitation(
                    autorisationService.obtenirKeycloakId(), exploitationId);
        }

        return convertirEnListeResponse(parcelles);
    }

    private Parcelle rechercherParcelle(Long id) {
        Optional<Parcelle> resultat = parcelleRepository.findById(id);

        if (resultat.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Parcelle introuvable avec l'identifiant : " + id
            );
        }

        return resultat.get();
    }

    private Exploitation rechercherExploitation(Long id) {
        Optional<Exploitation> resultat =
                exploitationRepository.findById(id);

        if (resultat.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Exploitation introuvable avec l'identifiant : " + id
            );
        }

        return resultat.get();
    }

    private ParcelleResponse convertirEnResponse(Parcelle parcelle) {
        ParcelleResponse response = new ParcelleResponse();

        response.setId(parcelle.getId());
        response.setCode(parcelle.getCode());
        response.setNom(parcelle.getNom());
        response.setSuperficie(parcelle.getSuperficie());
        response.setTypeSol(parcelle.getTypeSol());
        response.setTypeIrrigation(parcelle.getTypeIrrigation());
        response.setDescription(parcelle.getDescription());

        Exploitation exploitation = parcelle.getExploitation();

        response.setExploitationId(exploitation.getId());
        response.setExploitationCode(exploitation.getCode());
        response.setExploitationNom(exploitation.getNom());

        return response;
    }

    private List<ParcelleResponse> convertirEnListeResponse(
            List<Parcelle> parcelles) {

        List<ParcelleResponse> responses = new ArrayList<>();

        for (Parcelle parcelle : parcelles) {
            responses.add(convertirEnResponse(parcelle));
        }

        return responses;
    }
}
