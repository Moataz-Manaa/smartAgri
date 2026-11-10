package com.example.backend.dto.historique;

import com.example.backend.enums.TypeActivite;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ActiviteResponse {
    private Long id;
    private TypeActivite type;
    private LocalDate date;
    private String description;
    private String nomProduit;
    private BigDecimal quantite;
    private String unite;
    private String auteurNom;
    private String auteurPrenom;
}
