package com.example.backend.service;

import com.example.backend.dto.affectation.AffectationRequest;
import com.example.backend.dto.affectation.AffectationResponse;

import java.util.List;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AffectationService {
    PageResponse<AffectationResponse> consulterPage(Pageable pagination, Long parcelleId);

    AffectationResponse creer(AffectationRequest request);

    List<AffectationResponse> consulterToutes(Long parcelleId);

    void retirer(Long id);
}
