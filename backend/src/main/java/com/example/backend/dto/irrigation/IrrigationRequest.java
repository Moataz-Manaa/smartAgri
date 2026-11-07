package com.example.backend.dto.irrigation;

import com.example.backend.enums.UniteVolume;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class IrrigationRequest {
    @NotNull @Positive
    private Long plantationId;
    @NotNull @PastOrPresent
    private LocalDate dateIrrigation;
    @NotNull @Positive @Digits(integer = 12, fraction = 3)
    private BigDecimal volume;
    @NotNull
    private UniteVolume unite;
    @Size(max = 255)
    private String commentaire;
}
