package com.example.backend.repository;

import com.example.backend.entity.Fertilisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface FertilisationRepository extends JpaRepository<Fertilisation, Long> {
    Page<Fertilisation> findByPlantation_Id(Long plantationId, Pageable pagination);
    boolean existsByPlantation_Id(Long plantationId);
    boolean existsByPlantation_IdAndDateFertilisationBefore(Long plantationId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateFertilisationBefore(Long campagneId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateFertilisationAfter(Long campagneId, LocalDate date);
}
