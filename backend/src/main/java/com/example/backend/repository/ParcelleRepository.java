package com.example.backend.repository;

import com.example.backend.entity.Parcelle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ParcelleRepository extends JpaRepository<Parcelle, Long> {
    // Verrou court pendant une modification pour conserver les liens cohérents.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcelle p where p.id = :id")
    Optional<Parcelle> findPourModification(@Param("id") Long id);

    @Query("select p from Parcelle p where exists (select a.id from Affectation a " +
            "where a.parcelle = p and a.utilisateur.keycloakId = :keycloakId)")
    Page<Parcelle> findPageAccessible(@Param("keycloakId") String keycloakId, Pageable pagination);

    List<Parcelle> findByExploitation_Id(Long exploitationId);

    @Query("select a.parcelle from Affectation a where a.utilisateur.keycloakId = :keycloakId")
    List<Parcelle> findAccessibles(@Param("keycloakId") String keycloakId);

    @Query("select a.parcelle from Affectation a where a.utilisateur.keycloakId = :keycloakId " +
            "and a.parcelle.exploitation.id = :exploitationId")
    List<Parcelle> findAccessiblesParExploitation(@Param("keycloakId") String keycloakId,
                                                 @Param("exploitationId") Long exploitationId);

        @Query(
        value = "SELECT nextval('parcelle_code_seq')",
        nativeQuery = true
    )
    Long genererNumeroCode();
}
