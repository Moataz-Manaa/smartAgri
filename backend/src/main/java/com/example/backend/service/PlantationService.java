package com.example.backend.service;

import com.example.backend.dto.plantation.PlantationRequest;
import com.example.backend.dto.plantation.PlantationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.enums.StatutPlantation;
import org.springframework.data.domain.Pageable;

public interface PlantationService {
    PlantationResponse creer(PlantationRequest request);
    PlantationResponse modifier(Long id, PlantationRequest request);
    PlantationResponse consulterParId(Long id);
    PageResponse<PlantationResponse> consulterPage(Pageable pagination, Long parcelleId,
            Long campagneId, StatutPlantation statut);
}
