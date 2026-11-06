package com.example.backend.dto.variete;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VarieteResponse {
    private Long id;
    private String nom;
    private String description;
    private Long cultureId;
    private String cultureNom;
}
