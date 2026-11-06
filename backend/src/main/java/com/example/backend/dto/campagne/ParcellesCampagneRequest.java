package com.example.backend.dto.campagne;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ParcellesCampagneRequest {
    @NotEmpty(message = "Sélectionnez au moins une parcelle")
    private List<@NotNull @Positive Long> parcelleIds;
}
