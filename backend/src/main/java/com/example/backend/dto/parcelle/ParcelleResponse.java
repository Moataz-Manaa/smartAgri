package com.example.backend.dto.parcelle;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ParcelleResponse {

    private Long id;

    private String code;

    private String nom;

    private Double superficie;

    private String typeSol;

    private String typeIrrigation;

    private String description;

    private Long exploitationId;

    private String exploitationCode;

    private String exploitationNom;
}