package com.example.backend.entity;

import jakarta.persistence.*;
import com.example.backend.enums.UniteQuantite;
import java.math.BigDecimal;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "fertilisations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fertilisation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantation_id", nullable = false)
    private Plantation plantation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auteur_id", nullable = false, updatable = false)
    private Utilisateur auteur;

    @Column(nullable = false)
    private LocalDate dateFertilisation;

    @Column(nullable = false, length = 100)
    private String nomProduit;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UniteQuantite unite;

    @Column(length = 100)
    private String methodeApplication;

    @Column(length = 255)
    private String commentaire;

    @Column(length = 100)
    private String typeEngrais;


    @Version
    private Long version;
}
