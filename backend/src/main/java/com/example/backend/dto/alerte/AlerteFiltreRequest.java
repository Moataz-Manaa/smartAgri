package com.example.backend.dto.alerte;

import com.example.backend.enums.NiveauAlerte;
import com.example.backend.enums.StatutAlerte;
import com.example.backend.enums.TypeAlerte;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter
@Setter
public class AlerteFiltreRequest {
    @Positive
    private Long parcelleId;
    private TypeAlerte type;
    private NiveauAlerte niveau;
    private StatutAlerte statut;
    private Boolean lue;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateDebut;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFin;
}
