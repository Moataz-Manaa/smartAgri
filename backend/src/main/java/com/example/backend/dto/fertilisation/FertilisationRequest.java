package com.example.backend.dto.fertilisation;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.example.backend.enums.UniteQuantite;

@Getter
@Setter
public class FertilisationRequest {
    @NotNull @Positive
    private Long plantationId;
    @NotNull @PastOrPresent
    private LocalDate dateFertilisation;
    @NotBlank @Size(max = 100)
    private String nomProduit;
    @NotNull @Positive @Digits(integer = 12, fraction = 3)
    private BigDecimal quantite;
    @NotNull
    private UniteQuantite unite;
    @Size(max = 100)
    private String methodeApplication;
    @Size(max = 255)
    private String commentaire;
    @Size(max = 100)
    private String typeEngrais;

}
