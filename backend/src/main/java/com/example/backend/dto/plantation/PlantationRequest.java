package com.example.backend.dto.plantation;

import com.example.backend.enums.StatutPlantation;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class PlantationRequest {
    @NotNull @Positive
    private Long parcelleId;
    @NotNull @Positive
    private Long campagneId;
    @NotNull @Positive
    private Long varieteId;
    @NotNull
    private LocalDate datePlantation;
    @NotNull @Positive
    private Double superficie;
    @Positive
    private Integer quantitePlantee;
    private LocalDate dateRecoltePrevue;
    @Size(max = 255)
    private String commentaire;
    @NotNull
    private StatutPlantation statut;
}
