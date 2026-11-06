package com.example.backend.service.impl;

import com.example.backend.dto.variete.VarieteRequest;
import com.example.backend.dto.variete.VarieteResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Variete;
import com.example.backend.entity.Culture;
import com.example.backend.repository.CultureRepository;
import com.example.backend.repository.VarieteRepository;
import com.example.backend.service.VarieteService;
import com.example.backend.service.AutorisationService;
import com.example.backend.repository.PlantationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
public class VarieteServiceImpl implements VarieteService {
    private final VarieteRepository varieteRepository;
    private final PlantationRepository plantationRepository;
    private final CultureRepository cultureRepository;
    private final AutorisationService autorisationService;

    @Override
    public VarieteResponse creer(VarieteRequest request) {
        autorisationService.verifierAdministrateur();
        String nom = request.getNom().trim();
        String nomNormalise = nom.toLowerCase(Locale.ROOT);
        Culture culture = rechercherCulture(request.getCultureId());
        if (varieteRepository.existsByCulture_IdAndNomNormalise(culture.getId(), nomNormalise)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette variété existe déjà pour cette culture");
        }
        Variete variete = Variete.builder()
                .nom(nom).nomNormalise(nomNormalise).description(request.getDescription())
                .culture(culture).build();
        return convertirEnResponse(enregistrer(variete));
    }

    @Override
    public VarieteResponse modifier(Long id, VarieteRequest request) {
        autorisationService.verifierAdministrateur();
        Optional<Variete> resultat = varieteRepository.findPourModification(id);
        if (resultat.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variété introuvable");
        Variete variete = resultat.get();
        if (!variete.getCulture().getId().equals(request.getCultureId())
                && plantationRepository.existsByVariete_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une variété utilisée par une plantation ne peut pas changer de culture");
        }
        String nom = request.getNom().trim();
        String nomNormalise = nom.toLowerCase(Locale.ROOT);
        Culture culture = rechercherCulture(request.getCultureId());
        if (varieteRepository.existsByCulture_IdAndNomNormaliseAndIdNot(culture.getId(), nomNormalise, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette variété existe déjà pour cette culture");
        }
        variete.setNom(nom);
        variete.setNomNormalise(nomNormalise);
        variete.setDescription(request.getDescription());
        variete.setCulture(culture);
        return convertirEnResponse(enregistrer(variete));
    }

    @Override
    @Transactional(readOnly = true)
    public VarieteResponse consulterParId(Long id) {
        autorisationService.obtenirRole();
        return convertirEnResponse(rechercherVariete(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VarieteResponse> consulterToutes(Long cultureId) {
        autorisationService.obtenirRole();
        List<Variete> elements;
        if (cultureId == null) {
            elements = varieteRepository.findAll(Sort.by("nom").ascending());
        } else {
            rechercherCulture(cultureId);
            elements = varieteRepository.findByCulture_IdOrderByNomAsc(cultureId);
        }
        List<VarieteResponse> responses = new ArrayList<>();
        for (Variete element : elements) {
            responses.add(convertirEnResponse(element));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VarieteResponse> consulterPage(Pageable pagination, Long cultureId) {
        autorisationService.obtenirRole();
        Page<Variete> resultat;
        if (cultureId == null) {
            resultat = varieteRepository.findAll(pagination);
        } else {
            rechercherCulture(cultureId);
            resultat = varieteRepository.findByCulture_Id(cultureId, pagination);
        }
        List<VarieteResponse> contenu = new ArrayList<>();
        for (Variete element : resultat.getContent()) {
            contenu.add(convertirEnResponse(element));
        }
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    private Variete enregistrer(Variete variete) {
        try {
            return varieteRepository.saveAndFlush(variete);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette variété existe déjà pour cette culture");
        }
    }

    private Variete rechercherVariete(Long id) {
        Optional<Variete> resultat = varieteRepository.findById(id);
        if (resultat.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variété introuvable");
        }
        return resultat.get();
    }

    private Culture rechercherCulture(Long id) {
        Optional<Culture> resultat = cultureRepository.findById(id);
        if (resultat.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Culture introuvable");
        }
        return resultat.get();
    }

    private VarieteResponse convertirEnResponse(Variete variete) {
        VarieteResponse response = new VarieteResponse();
        response.setId(variete.getId());
        response.setNom(variete.getNom());
        response.setDescription(variete.getDescription());
        response.setCultureId(variete.getCulture().getId());
        response.setCultureNom(variete.getCulture().getNom());
        return response;
    }
}
