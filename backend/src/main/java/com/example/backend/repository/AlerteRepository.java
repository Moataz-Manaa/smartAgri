package com.example.backend.repository;

import com.example.backend.entity.Alerte;
import com.example.backend.enums.StatutAlerte;
import com.example.backend.enums.TypeAlerte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface AlerteRepository extends JpaRepository<Alerte, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alerte a where a.id = :id")
    Optional<Alerte> findPourLecture(@Param("id") Long id);

    Optional<Alerte> findByParcelle_IdAndTypeAndDatePrevisionAndLatitudeAndLongitude(
            Long parcelleId, TypeAlerte type, Instant datePrevision, Double latitude, Double longitude);

    List<Alerte> findByParcelle_IdAndStatut(Long parcelleId, StatutAlerte statut);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Alerte a set a.statut = :expiree, a.dateCloture = :maintenant, "
            + "a.motifCloture = 'Échéance météo passée', a.version = a.version + 1 "
            + "where a.statut = :active and a.datePrevision <= :maintenant")
    int expirer(@Param("maintenant") Instant maintenant, @Param("active") StatutAlerte active,
                @Param("expiree") StatutAlerte expiree);
}
