package com.example.backend.repository;

import com.example.backend.entity.Observation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface ObservationRepository extends JpaRepository<Observation, Long> {
    Page<Observation> findByPlantation_Id(Long plantationId, Pageable pagination);
    boolean existsByPlantation_Id(Long plantationId);
    boolean existsByPlantation_IdAndDateObservationBefore(Long plantationId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateObservationBefore(Long campagneId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateObservationAfter(Long campagneId, LocalDate date);
}
