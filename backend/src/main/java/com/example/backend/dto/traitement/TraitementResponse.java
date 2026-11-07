package com.example.backend.dto.traitement;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.example.backend.enums.UniteQuantite;

@Getter
@Setter
public class TraitementResponse {
    private Long id;
    private Long plantationId;
    private LocalDate dateTraitement;
    private String nomProduit;
    private BigDecimal quantite;
    private UniteQuantite unite;
    private String methodeApplication;
    private String commentaire;
    private String cible;

    private String auteurNom;
    private String auteurPrenom;
    private boolean campagneCloturee;
}
