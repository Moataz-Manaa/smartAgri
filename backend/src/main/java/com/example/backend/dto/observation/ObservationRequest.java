package com.example.backend.dto.observation;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class ObservationRequest {
    @NotNull @Positive
    private Long plantationId;
    @NotNull @PastOrPresent
    private LocalDate dateObservation;
    @NotBlank @Size(max = 2000)
    private String description;
}
