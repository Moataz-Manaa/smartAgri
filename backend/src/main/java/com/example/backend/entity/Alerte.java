package com.example.backend.entity;

import com.example.backend.enums.NiveauAlerte;
import com.example.backend.enums.StatutAlerte;
import com.example.backend.enums.TypeAlerte;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "alertes", uniqueConstraints = @UniqueConstraint(name = "uk_alerte_creneau_point",
        columnNames = {"parcelle_id", "type", "date_prevision", "latitude", "longitude"}),
        indexes = @Index(name = "idx_alerte_statut_echeance", columnList = "statut,date_prevision"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alerte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcelle_id", nullable = false)
    private Parcelle parcelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeAlerte type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NiveauAlerte niveau;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutAlerte statut;

    @Column(nullable = false, length = 500)
    private String message;
    @Column(nullable = false)
    private Double valeur;
    @Column(nullable = false)
    private Double seuil;
    @Column(nullable = false, length = 20)
    private String unite;
    @Column(nullable = false)
    private Double latitude;
    @Column(nullable = false)
    private Double longitude;
    @Column(nullable = false, name = "date_prevision")
    private Instant datePrevision;
    @Column(nullable = false)
    private Instant debutPeriode;
    @Column(nullable = false)
    private Instant dateDetection;
    @Column(nullable = false)
    private Instant derniereVerification;
    @Column(nullable = false)
    private Instant dateRecuperationMeteo;
    private Instant dateCloture;
    @Column(length = 255)
    private String motifCloture;
    @Version
    private Long version;
}
