package com.example.backend.entity;

import com.example.backend.enums.StatutPlantation;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "plantations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plantation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcelle_id", nullable = false)
    private Parcelle parcelle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campagne_id", nullable = false)
    private Campagne campagne;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variete_id", nullable = false)
    private Variete variete;

    @Column(nullable = false)
    private LocalDate datePlantation;

    @Column(nullable = false)
    private Double superficie;

    private Integer quantitePlantee;
    private LocalDate dateRecoltePrevue;

    @Column(length = 255)
    private String commentaire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutPlantation statut;

    @Version
    private Long version;
}
