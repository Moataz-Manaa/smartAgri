package com.example.backend.dto.parcelle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ParcelleRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(
        max = 100,
        message = "Le nom ne doit pas dépasser 100 caractères"
    )
    private String nom;

    @NotNull(message = "La superficie est obligatoire")
    @Positive(message = "La superficie doit être supérieure à zéro")
    private Double superficie;

    @Size(
        max = 255,
        message = "Le type de sol ne doit pas dépasser 255 caractères"
    )
    private String typeSol;

    @Size(
        max = 255,
        message = "Le type d'irrigation ne doit pas dépasser 255 caractères"
    )
    private String typeIrrigation;

    @Size(
        max = 255,
        message = "La description ne doit pas dépasser 255 caractères"
    )
    private String description;

    @NotNull(message = "L'exploitation est obligatoire")
    @Positive(message = "L'identifiant de l'exploitation doit être positif")
    private Long exploitationId;
}