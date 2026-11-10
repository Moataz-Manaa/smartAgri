package com.example.backend.dto.recolte;

import com.example.backend.enums.UniteRecolte;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class RecolteResponse {
    private Long id;
    private Long plantationId;
    private LocalDate dateRecolte;
    private BigDecimal quantite;
    private UniteRecolte unite;
    private BigDecimal quantiteKilogrammes;
    private String commentaire;
    private String auteurNom;
    private String auteurPrenom;
    private boolean campagneCloturee;
}
