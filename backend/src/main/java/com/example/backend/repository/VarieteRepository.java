package com.example.backend.repository;

import com.example.backend.entity.Variete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface VarieteRepository extends JpaRepository<Variete, Long> {
    // Verrou court pendant une modification pour conserver les liens cohérents.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Variete v where v.id = :id")
    Optional<Variete> findPourModification(@Param("id") Long id);

    boolean existsByCulture_IdAndNomNormalise(Long cultureId, String nomNormalise);

    boolean existsByCulture_IdAndNomNormaliseAndIdNot(Long cultureId, String nomNormalise, Long id);

    Page<Variete> findByCulture_Id(Long cultureId, Pageable pagination);

    List<Variete> findByCulture_IdOrderByNomAsc(Long cultureId);
}
