package com.example.backend.dto.affectation;

import com.example.backend.enums.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AffectationResponse {

    private Long id;
    private Long utilisateurId;
    private String utilisateurNom;
    private String utilisateurPrenom;
    private String utilisateurEmail;
    private Role role;
    private Long parcelleId;
    private String parcelleCode;
    private String parcelleNom;
    private String exploitationNom;
}
