package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "observations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Observation {
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
    private LocalDate dateObservation;

    @Column(nullable = false, length = 2000)
    private String description;

    @Version
    private Long version;
}
