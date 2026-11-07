package com.example.backend.dto.observation;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class ObservationResponse {
    private Long id;
    private Long plantationId;
    private LocalDate dateObservation;
    private String description;
    private String auteurNom;
    private String auteurPrenom;
    private boolean campagneCloturee;
}
