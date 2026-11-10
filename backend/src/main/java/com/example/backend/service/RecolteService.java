package com.example.backend.service;

import com.example.backend.dto.recolte.RecolteRequest;
import com.example.backend.dto.recolte.RecolteResponse;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import com.example.backend.dto.recolte.TotalRecolteResponse;

public interface RecolteService {
    TotalRecolteResponse consulterTotal(Long plantationId);
    RecolteResponse creer(RecolteRequest request);
    RecolteResponse modifier(Long id, RecolteRequest request);
    RecolteResponse consulterParId(Long id);
    PageResponse<RecolteResponse> consulterPage(Long plantationId, Pageable pagination);
}
