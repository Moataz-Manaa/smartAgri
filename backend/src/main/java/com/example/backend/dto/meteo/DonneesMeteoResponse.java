package com.example.backend.dto.meteo;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class DonneesMeteoResponse {
    private final Instant recupereesLe;
    private final Integer decalageUtcSecondes;
    private final ConditionsMeteoResponse conditions;
    private final List<PrevisionMeteoResponse> previsions;
}
