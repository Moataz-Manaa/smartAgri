package com.example.backend.service;

import com.example.backend.dto.parcelle.ParcelleRequest;
import com.example.backend.dto.parcelle.ParcelleResponse;

import java.util.List;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ParcelleService {
    PageResponse<ParcelleResponse> consulterPage(Pageable pagination);

    ParcelleResponse creer(ParcelleRequest request);

    ParcelleResponse modifier(Long id, ParcelleRequest request);

    ParcelleResponse consulterParId(Long id);

    List<ParcelleResponse> consulterToutes();

    List<ParcelleResponse> consulterParExploitation(Long exploitationId);
}
