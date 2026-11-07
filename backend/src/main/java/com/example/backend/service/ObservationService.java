package com.example.backend.service;

import com.example.backend.dto.observation.ObservationRequest;
import com.example.backend.dto.observation.ObservationResponse;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ObservationService {
    ObservationResponse creer(ObservationRequest request);
    ObservationResponse modifier(Long id, ObservationRequest request);
    ObservationResponse consulterParId(Long id);
    PageResponse<ObservationResponse> consulterPage(Long plantationId, Pageable pagination);
}
