package com.example.backend.config;

import com.example.backend.service.AlerteGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.alertes", name = "planification-active", havingValue = "true", matchIfMissing = true)
public class AlertePlanification {
    private final AlerteGenerationService alerteGenerationService;

    @Scheduled(fixedDelayString = "${app.alertes.intervalle-ms:1800000}",
            initialDelayString = "${app.alertes.delai-initial-ms:60000}")
    public void executer() {
        alerteGenerationService.generer();
    }
}
