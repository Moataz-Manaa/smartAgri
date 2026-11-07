package com.example.backend.entity;

import com.example.backend.enums.UniteVolume;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "irrigations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Irrigation {
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
    private LocalDate dateIrrigation;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal volume;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UniteVolume unite;

    // Valeur convertie pour additionner correctement litres et mètres cubes.
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal volumeMetresCubes;

    @Column(length = 255)
    private String commentaire;

    @Version
    private Long version;
}
