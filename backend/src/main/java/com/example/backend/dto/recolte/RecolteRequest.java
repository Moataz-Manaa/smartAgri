package com.example.backend.dto.recolte;

import com.example.backend.enums.UniteRecolte;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class RecolteRequest {
    @NotNull @Positive
    private Long plantationId;
    @NotNull @PastOrPresent
    private LocalDate dateRecolte;
    @NotNull @Positive @Digits(integer = 12, fraction = 3)
    private BigDecimal quantite;
    @NotNull
    private UniteRecolte unite;
    @Size(max = 255)
    private String commentaire;
}
