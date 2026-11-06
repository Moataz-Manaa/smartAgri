package com.example.backend.entity;

import com.example.backend.enums.StatutCampagne;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "campagnes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Campagne {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, unique = true, length = 100)
    private String nomNormalise;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutCampagne statut;

    @ManyToMany
    @JoinTable(name = "campagne_parcelles",
            joinColumns = @JoinColumn(name = "campagne_id"),
            inverseJoinColumns = @JoinColumn(name = "parcelle_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"campagne_id", "parcelle_id"}))
    @Builder.Default
    private List<Parcelle> parcelles = new ArrayList<>();

    // Empêche deux enregistrements simultanés d'écraser leurs changements.
    @Version
    private Long version;
}
