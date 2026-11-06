package com.example.backend.repository;

import com.example.backend.entity.Campagne;
import com.example.backend.enums.StatutCampagne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CampagneRepository extends JpaRepository<Campagne, Long> {
    // Verrou court pendant une modification pour conserver les liens cohérents.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campagne c where c.id = :id")
    Optional<Campagne> findPourModification(@Param("id") Long id);

    @Query("select c from Campagne c where (:statut is null or c.statut = :statut) " +
            "and exists (select a.id from Affectation a where a.utilisateur.keycloakId = :keycloakId " +
            "and a.parcelle member of c.parcelles)")
    Page<Campagne> findPageAccessible(@Param("keycloakId") String keycloakId,
                                     @Param("statut") StatutCampagne statut, Pageable pagination);

    @Query("select c from Campagne c where (:statut is null or c.statut = :statut) " +
            "and exists (select a.id from Affectation a where a.utilisateur.keycloakId = :keycloakId " +
            "and a.parcelle member of c.parcelles)")
    List<Campagne> findAccessibles(@Param("keycloakId") String keycloakId,
                                   @Param("statut") StatutCampagne statut, Sort tri);
    boolean existsByNomNormalise(String nomNormalise);
    boolean existsByNomNormaliseAndIdNot(String nomNormalise, Long id);
    Page<Campagne> findByStatut(StatutCampagne statut, Pageable pagination);
    List<Campagne> findByStatut(StatutCampagne statut, Sort tri);
}
