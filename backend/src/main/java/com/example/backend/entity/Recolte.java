package com.example.backend.entity;

import com.example.backend.enums.UniteRecolte;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recoltes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recolte {
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
    private LocalDate dateRecolte;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UniteRecolte unite;

    // Valeur convertie pour additionner les kilogrammes et les tonnes.
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantiteKilogrammes;

    @Column(length = 255)
    private String commentaire;

    @Version
    private Long version;
}
