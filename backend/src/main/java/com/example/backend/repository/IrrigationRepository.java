package com.example.backend.repository;

import com.example.backend.entity.Irrigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IrrigationRepository extends JpaRepository<Irrigation, Long> {
    Page<Irrigation> findByPlantation_Id(Long plantationId, Pageable pagination);
    boolean existsByPlantation_Id(Long plantationId);
    @Query("select coalesce(sum(i.volumeMetresCubes), 0) from Irrigation i where i.plantation.id = :plantationId")
    BigDecimal calculerTotalMetresCubes(@Param("plantationId") Long plantationId);
    boolean existsByPlantation_IdAndDateIrrigationBefore(Long plantationId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateIrrigationBefore(Long campagneId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateIrrigationAfter(Long campagneId, LocalDate date);
}
