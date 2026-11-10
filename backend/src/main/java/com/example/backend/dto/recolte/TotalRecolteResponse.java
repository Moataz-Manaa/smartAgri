package com.example.backend.dto.recolte;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TotalRecolteResponse {
    private BigDecimal quantiteTotaleKilogrammes;
    private Double superficieHectares;
    private BigDecimal rendementKgParHectare;
}
