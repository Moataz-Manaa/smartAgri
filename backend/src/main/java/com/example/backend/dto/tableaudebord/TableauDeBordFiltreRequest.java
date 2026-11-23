package com.example.backend.dto.tableaudebord;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter
@Setter
public class TableauDeBordFiltreRequest {
    @Positive private Long parcelleId;
    @Positive private Long campagneId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dateDebut;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dateFin;
}
