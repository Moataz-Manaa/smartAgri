package com.example.backend.dto.tableaudebord;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class AlertesTableauDeBordResponse {
    private long actives;
    private long activesNonLues;
    private Instant calculeLe;
}
