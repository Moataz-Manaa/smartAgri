package com.example.backend.service;

import com.example.backend.dto.utilisateur.UtilisateurCreationRequest;
import com.example.backend.dto.utilisateur.UtilisateurModificationRequest;
import com.example.backend.dto.utilisateur.UtilisateurResponse;

import java.util.List;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

public interface UtilisateurService {
    PageResponse<UtilisateurResponse> consulterPage(Pageable pagination);

    UtilisateurResponse creer(UtilisateurCreationRequest request);

    UtilisateurResponse modifier(
            Long id,
            UtilisateurModificationRequest request
    );

    UtilisateurResponse consulterParId(Long id);

    List<UtilisateurResponse> consulterTous();

    UtilisateurResponse activer(Long id);

    UtilisateurResponse desactiver(Long id);
}
