package com.example.backend.dto.tableaudebord;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class SyntheseResponse {
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private long nombreParcelles;
    private long nombreCampagnes;
    private long nombrePlantations;
    private long nombreActivites;
    private BigDecimal volumeIrrigationMetresCubes;
    private BigDecimal quantiteRecolteeKilogrammes;
}
