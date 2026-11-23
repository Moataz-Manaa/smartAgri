package com.example.backend.dto.meteo;

import com.example.backend.enums.StatutMeteo;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MeteoResponse {
    private final Long parcelleId;
    private final Long exploitationId;
    private final String exploitationNom;
    private final Double latitude;
    private final Double longitude;
    private final StatutMeteo statut;
    private final String message;
    @Builder.Default
    private final String source = "OpenWeather";
    @Builder.Default
    private final String lienSource = "https://openweathermap.org/";
    private final DonneesMeteoResponse donnees;
}
