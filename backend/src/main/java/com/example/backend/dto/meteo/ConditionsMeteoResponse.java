package com.example.backend.dto.meteo;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

@Getter
@Builder
public class ConditionsMeteoResponse {
    private final Instant dateDonnees;
    private final Double temperatureCelsius;
    private final Double humiditePourcent;
    private final Double pluieDerniereHeureMillimetres;
    private final String description;
}
