package com.example.backend.service;

import com.example.backend.dto.fertilisation.FertilisationRequest;
import com.example.backend.dto.fertilisation.FertilisationResponse;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

public interface FertilisationService {
    FertilisationResponse creer(FertilisationRequest request);
    FertilisationResponse modifier(Long id, FertilisationRequest request);
    FertilisationResponse consulterParId(Long id);
    PageResponse<FertilisationResponse> consulterPage(Long plantationId, Pageable pagination);
}
