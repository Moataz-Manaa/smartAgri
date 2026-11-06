package com.example.backend.service;

import com.example.backend.dto.campagne.CampagneRequest;
import com.example.backend.dto.campagne.CampagneResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.enums.StatutCampagne;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface CampagneService {
    CampagneResponse creer(CampagneRequest request);
    CampagneResponse modifier(Long id, CampagneRequest request);
    CampagneResponse consulterParId(Long id);
    CampagneResponse changerStatut(Long id, StatutCampagne statut);
    CampagneResponse associerParcelles(Long id, List<Long> parcelleIds);
    List<CampagneResponse> consulterToutes(StatutCampagne statut);
    PageResponse<CampagneResponse> consulterPage(Pageable pagination, StatutCampagne statut);
}
