package com.example.backend.service.impl;

import com.example.backend.dto.historique.ActiviteResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Plantation;
import com.example.backend.enums.TypeActivite;
import com.example.backend.repository.HistoriqueRepository;
import com.example.backend.repository.PlantationRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.HistoriqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoriqueServiceImpl implements HistoriqueService {
    private final HistoriqueRepository historiqueRepository;
    private final PlantationRepository plantationRepository;
    private final AutorisationService autorisationService;

    @Override
    public PageResponse<ActiviteResponse> consulterPage(Long plantationId, TypeActivite type,
            LocalDate dateDebut, LocalDate dateFin, Pageable pagination) {
        Plantation plantation = plantationRepository.findById(plantationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantation introuvable"));
        // Vérifier l'accès avant toute lecture des activités ou de leur nombre.
        autorisationService.verifierAccesParcelle(plantation.getParcelle().getId());
        if (dateDebut != null && dateFin != null && dateDebut.isAfter(dateFin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de début doit précéder ou être égale à la date de fin");
        }
        return historiqueRepository.consulterPage(plantationId, type, dateDebut, dateFin, pagination);
    }
}
