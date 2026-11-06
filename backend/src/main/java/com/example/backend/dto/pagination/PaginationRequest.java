package com.example.backend.dto.pagination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaginationRequest {
    @Min(value = 0, message = "La page doit être positive ou nulle")
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins égale à 1")
    @Max(value = 100, message = "La taille ne doit pas dépasser 100")
    private int taille = 10;
}
