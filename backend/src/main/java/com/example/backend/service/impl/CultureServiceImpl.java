package com.example.backend.service.impl;

import com.example.backend.dto.culture.CultureRequest;
import com.example.backend.dto.culture.CultureResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Culture;
import com.example.backend.repository.CultureRepository;
import com.example.backend.service.CultureService;
import com.example.backend.service.AutorisationService;
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
public class CultureServiceImpl implements CultureService {
    private final CultureRepository cultureRepository;
    private final AutorisationService autorisationService;

    @Override
    public CultureResponse creer(CultureRequest request) {
        autorisationService.verifierAdministrateur();
        String nom = request.getNom().trim();
        String nomNormalise = nom.toLowerCase(Locale.ROOT);
        
        if (cultureRepository.existsByNomNormalise(nomNormalise)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette culture existe déjà");
        }
        Culture culture = Culture.builder()
                .nom(nom).nomNormalise(nomNormalise).description(request.getDescription())
                .type(request.getType()).build();
        return convertirEnResponse(enregistrer(culture));
    }

    @Override
    public CultureResponse modifier(Long id, CultureRequest request) {
        autorisationService.verifierAdministrateur();
        Culture culture = rechercherCulture(id);
        String nom = request.getNom().trim();
        String nomNormalise = nom.toLowerCase(Locale.ROOT);
        
        if (cultureRepository.existsByNomNormaliseAndIdNot(nomNormalise, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette culture existe déjà");
        }
        culture.setNom(nom);
        culture.setNomNormalise(nomNormalise);
        culture.setDescription(request.getDescription());
        culture.setType(request.getType());
        return convertirEnResponse(enregistrer(culture));
    }

    @Override
    @Transactional(readOnly = true)
    public CultureResponse consulterParId(Long id) {
        autorisationService.obtenirRole();
        return convertirEnResponse(rechercherCulture(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CultureResponse> consulterToutes() {
        autorisationService.obtenirRole();
        List<Culture> elements;
        elements = cultureRepository.findAll(Sort.by("nom").ascending());
        List<CultureResponse> responses = new ArrayList<>();
        for (Culture element : elements) {
            responses.add(convertirEnResponse(element));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CultureResponse> consulterPage(Pageable pagination) {
        autorisationService.obtenirRole();
        Page<Culture> resultat;
        resultat = cultureRepository.findAll(pagination);
        List<CultureResponse> contenu = new ArrayList<>();
        for (Culture element : resultat.getContent()) {
            contenu.add(convertirEnResponse(element));
        }
        return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements(), resultat.getTotalPages());
    }

    private Culture enregistrer(Culture culture) {
        try {
            return cultureRepository.saveAndFlush(culture);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette culture existe déjà");
        }
    }

    private Culture rechercherCulture(Long id) {
        Optional<Culture> resultat = cultureRepository.findById(id);
        if (resultat.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Culture introuvable");
        }
        return resultat.get();
    }

    private CultureResponse convertirEnResponse(Culture culture) {
        CultureResponse response = new CultureResponse();
        response.setId(culture.getId());
        response.setNom(culture.getNom());
        response.setDescription(culture.getDescription());
        response.setType(culture.getType());
        return response;
    }
}
