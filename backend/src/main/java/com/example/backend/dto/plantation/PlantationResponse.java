package com.example.backend.dto.plantation;

import com.example.backend.enums.StatutPlantation;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class PlantationResponse {
    private Long id;
    private Long parcelleId;
    private String parcelleCode;
    private String parcelleNom;
    private Long campagneId;
    private String campagneNom;
    private boolean campagneCloturee;
    private Long varieteId;
    private String varieteNom;
    private Long cultureId;
    private String cultureNom;
    private LocalDate datePlantation;
    private Double superficie;
    private Integer quantitePlantee;
    private LocalDate dateRecoltePrevue;
    private String commentaire;
    private StatutPlantation statut;
}
