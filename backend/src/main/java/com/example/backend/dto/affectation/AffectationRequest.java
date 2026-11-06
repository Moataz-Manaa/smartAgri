package com.example.backend.dto.affectation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AffectationRequest {

    @NotNull(message = "L’utilisateur est obligatoire")
    @Positive(message = "L’identifiant utilisateur doit être positif")
    private Long utilisateurId;

    @NotNull(message = "La parcelle est obligatoire")
    @Positive(message = "L’identifiant de parcelle doit être positif")
    private Long parcelleId;
}
