package com.example.backend.dto.utilisateur;

import com.example.backend.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UtilisateurCreationRequest {

    @NotBlank(message = "L’identifiant de connexion est obligatoire")
    @Size(max = 100, message = "L’identifiant ne doit pas dépasser 100 caractères")
    private String username;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
    private String prenom;

    @NotBlank(message = "L’email est obligatoire")
    @Email(message = "L’adresse email est invalide")
    @Size(max = 255, message = "L’email ne doit pas dépasser 255 caractères")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(
        min = 8,
        max = 128,
        message = "Le mot de passe doit contenir entre 8 et 128 caractères"
    )
    private String motDePasse;

    @NotNull(message = "Le rôle est obligatoire")
    private Role role;
}