package com.example.backend.service;

import com.example.backend.dto.alerte.AlerteFiltreRequest;
import com.example.backend.dto.alerte.AlerteResponse;
import com.example.backend.dto.alerte.CompteurAlertesResponse;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AlerteService {
    PageResponse<AlerteResponse> consulterPage(AlerteFiltreRequest filtre, Pageable pagination);
    AlerteResponse consulterParId(Long id);
    AlerteResponse marquerLue(Long id);
    CompteurAlertesResponse consulterCompteur(Long parcelleId);
}
