package com.example.backend.dto.irrigation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TotalIrrigationResponse {
    private BigDecimal volumeTotalMetresCubes;
}
