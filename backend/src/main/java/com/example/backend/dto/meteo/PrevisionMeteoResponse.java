package com.example.backend.dto.meteo;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

@Getter
@Builder
public class PrevisionMeteoResponse {
    private final Instant datePrevision;
    private final Double temperatureCelsius;
    private final Double humiditePourcent;
    private final Double probabilitePrecipitationsPourcent;
    private final Double pluieTroisHeuresMillimetres;
    private final String description;
}
