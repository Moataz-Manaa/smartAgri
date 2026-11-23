package com.example.backend.service.impl;

import com.example.backend.dto.meteo.MeteoResponse;
import com.example.backend.entity.Parcelle;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.MeteoCollecteService;
import com.example.backend.service.MeteoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MeteoServiceImpl implements MeteoService {
    private final ParcelleRepository parcelleRepository;
    private final AutorisationService autorisationService;
    private final MeteoCollecteService meteoCollecteService;

    @Override
    public MeteoResponse consulterParParcelle(Long parcelleId) {
        Parcelle parcelle = parcelleRepository.findAvecExploitation(parcelleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable"));
        // L'API contrôle les droits avant le cache partagé avec les alertes.
        autorisationService.verifierAccesParcelle(parcelleId);
        return meteoCollecteService.collecter(parcelle);
    }
}
