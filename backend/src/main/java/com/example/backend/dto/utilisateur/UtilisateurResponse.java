package com.example.backend.dto.utilisateur;

import com.example.backend.enums.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UtilisateurResponse {

    private Long id;

    private String keycloakId;

    private String username;

    private String nom;

    private String prenom;

    private String email;

    private Role role;

    private boolean actif;
}