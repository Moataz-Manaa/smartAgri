package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "lectures_alertes", uniqueConstraints = @UniqueConstraint(name = "uk_lecture_alerte_compte",
        columnNames = {"alerte_id", "keycloak_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LectureAlerte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alerte_id", nullable = false, updatable = false)
    private Alerte alerte;

    // L'identité provient du token : un administrateur peut lire sans profil métier local.
    @Column(name = "keycloak_id", nullable = false, updatable = false)
    private String keycloakId;

    @Column(nullable = false, updatable = false)
    private Instant dateLecture;
}
