package com.example.backend;

import com.example.backend.config.AlerteRegles;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AlerteReglesTests {
    @Test void accepterLesReglesInitialesEtDesSeuilsAdaptes() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AlerteRegles regles = new AlerteRegles();
            assertThat(factory.getValidator().validate(regles)).isEmpty();
            regles.setChaleurCelsius(32);
            regles.setChaleurCritiqueCelsius(38);
            assertThat(factory.getValidator().validate(regles)).isEmpty();
        }
    }

    @Test void refuserLesNiveauxCritiquesInversesEtLesReglesQuiSeChevauchent() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AlerteRegles regles = new AlerteRegles();
            regles.setChaleurCritiqueCelsius(30);
            assertThat(factory.getValidator().validate(regles)).isNotEmpty();
            regles = new AlerteRegles();
            regles.setGelCritiqueCelsius(1);
            assertThat(factory.getValidator().validate(regles)).isNotEmpty();
            regles = new AlerteRegles();
            regles.setPluieCritiqueTroisHeuresMm(10);
            assertThat(factory.getValidator().validate(regles)).isNotEmpty();
            regles = new AlerteRegles();
            regles.setGelCelsius(36);
            assertThat(factory.getValidator().validate(regles)).isNotEmpty();
        }
    }

    @Test void refuserLesDelaisEtValeursIncoherents() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AlerteRegles regles = new AlerteRegles();
            regles.setIntervalleMs(0);
            regles.setHorizonHeures(200);
            regles.setDonneesMaxSecondes(0);
            regles.setChaleurCelsius(Double.NaN);
            assertThat(factory.getValidator().validate(regles)).hasSizeGreaterThanOrEqualTo(4);
        }
    }
}
