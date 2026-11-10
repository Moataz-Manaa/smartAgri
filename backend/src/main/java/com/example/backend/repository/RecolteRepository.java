package com.example.backend.repository;

import com.example.backend.entity.Recolte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecolteRepository extends JpaRepository<Recolte, Long> {
    Page<Recolte> findByPlantation_Id(Long plantationId, Pageable pagination);
    boolean existsByPlantation_Id(Long plantationId);
    @Query("select coalesce(sum(i.quantiteKilogrammes), 0) from Recolte i where i.plantation.id = :plantationId")
    BigDecimal calculerTotalKilogrammes(@Param("plantationId") Long plantationId);
    boolean existsByPlantation_IdAndDateRecolteBefore(Long plantationId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateRecolteBefore(Long campagneId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateRecolteAfter(Long campagneId, LocalDate date);
}
