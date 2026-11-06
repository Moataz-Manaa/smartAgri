package com.example.backend.dto.culture;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CultureResponse {
    private Long id;
    private String nom;
    private String description;
    private String type;
}
