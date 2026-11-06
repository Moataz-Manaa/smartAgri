package com.example.backend.dto.campagne;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ParcelleCampagneResponse {
    private Long id;
    private String code;
    private String nom;
    private Long exploitationId;
    private String exploitationNom;
}
