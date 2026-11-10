package com.example.backend.service;

import com.example.backend.dto.historique.ActiviteResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.enums.TypeActivite;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface HistoriqueService {
    PageResponse<ActiviteResponse> consulterPage(Long plantationId, TypeActivite type,
            LocalDate dateDebut, LocalDate dateFin, Pageable pagination);
}
