package com.example.backend.service.impl;

import com.example.backend.dto.meteo.MeteoResponse;
import com.example.backend.entity.Parcelle;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.service.AlerteCalculService;
import com.example.backend.service.AlerteGenerationService;
import com.example.backend.service.MeteoCollecteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlerteGenerationServiceImpl implements AlerteGenerationService {
    private final ParcelleRepository parcelleRepository;
    private final MeteoCollecteService meteoCollecteService;
    private final AlerteCalculService alerteCalculService;

    @Override
    public synchronized void generer() {
        alerteCalculService.expirer();
        int page = 0;
        List<Long> ids;
        do {
            ids = parcelleRepository.findIdsPourAlertes(PageRequest.of(page, 50));
            for (Long id : ids) {
                try {
                    Parcelle parcelle = parcelleRepository.findAvecExploitation(id).orElse(null);
                    if (parcelle == null) continue;
                    // La collecte et son cache ne demandent pas de session utilisateur pour cette tâche interne.
                    MeteoResponse meteo = meteoCollecteService.collecter(parcelle);
                    alerteCalculService.evaluer(id, meteo);
                } catch (RuntimeException exception) {
                    // Ne pas journaliser le message externe : il pourrait contenir une URL avec une clé.
                    log.warn("Calcul d'alertes ignoré pour la parcelle {} ({})", id, exception.getClass().getSimpleName());
                }
            }
            page++;
        } while (ids.size() == 50);
    }
}
