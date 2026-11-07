package com.example.backend.service;

import com.example.backend.dto.traitement.TraitementRequest;
import com.example.backend.dto.traitement.TraitementResponse;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

public interface TraitementService {
    TraitementResponse creer(TraitementRequest request);
    TraitementResponse modifier(Long id, TraitementRequest request);
    TraitementResponse consulterParId(Long id);
    PageResponse<TraitementResponse> consulterPage(Long plantationId, Pageable pagination);
}
