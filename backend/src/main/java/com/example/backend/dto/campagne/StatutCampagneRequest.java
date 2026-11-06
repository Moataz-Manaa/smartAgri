package com.example.backend.dto.campagne;

import com.example.backend.enums.StatutCampagne;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StatutCampagneRequest {
    @NotNull(message = "Le statut est obligatoire")
    private StatutCampagne statut;
}
