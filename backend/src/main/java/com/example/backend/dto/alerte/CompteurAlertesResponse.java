package com.example.backend.dto.alerte;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class CompteurAlertesResponse {
    private long activesNonLues;
    private Instant calculeLe;
}
