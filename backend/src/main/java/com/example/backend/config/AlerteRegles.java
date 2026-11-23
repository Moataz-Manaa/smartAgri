package com.example.backend.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.alertes")
@Validated
@Getter
@Setter
public class AlerteRegles {
    @DecimalMin("-150") @DecimalMax("100")
    private double chaleurCelsius = 35;
    @DecimalMin("-150") @DecimalMax("100")
    private double chaleurCritiqueCelsius = 40;
    @DecimalMin("-150") @DecimalMax("100")
    private double gelCelsius = 0;
    @DecimalMin("-150") @DecimalMax("100")
    private double gelCritiqueCelsius = -3;
    @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000")
    private double pluieTroisHeuresMm = 20;
    @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000")
    private double pluieCritiqueTroisHeuresMm = 40;
    @Min(1) @Max(120)
    private int horizonHeures = 24;
    @Min(60) @Max(3600)
    private long donneesMaxSecondes = 900;
    @Min(60000)
    private long intervalleMs = 1800000;
    @Min(1000)
    private long delaiInitialMs = 60000;
    private boolean planificationActive = true;

    @AssertTrue(message = "Les seuils critiques doivent être plus sévères et le gel inférieur à la chaleur")
    public boolean isSeuilsCoherents() {
        return gelCritiqueCelsius < gelCelsius && gelCelsius < chaleurCelsius
                && chaleurCelsius < chaleurCritiqueCelsius
                && pluieTroisHeuresMm < pluieCritiqueTroisHeuresMm;
    }
}
