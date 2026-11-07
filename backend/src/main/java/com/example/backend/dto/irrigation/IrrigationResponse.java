package com.example.backend.dto.irrigation;

import com.example.backend.enums.UniteVolume;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class IrrigationResponse {
    private Long id;
    private Long plantationId;
    private LocalDate dateIrrigation;
    private BigDecimal volume;
    private UniteVolume unite;
    private BigDecimal volumeMetresCubes;
    private String commentaire;
    private String auteurNom;
    private String auteurPrenom;
    private boolean campagneCloturee;
}
