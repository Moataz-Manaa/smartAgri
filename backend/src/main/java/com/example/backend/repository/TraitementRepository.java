package com.example.backend.repository;

import com.example.backend.entity.Traitement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface TraitementRepository extends JpaRepository<Traitement, Long> {
    Page<Traitement> findByPlantation_Id(Long plantationId, Pageable pagination);
    boolean existsByPlantation_Id(Long plantationId);
    boolean existsByPlantation_IdAndDateTraitementBefore(Long plantationId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateTraitementBefore(Long campagneId, LocalDate date);
    boolean existsByPlantation_Campagne_IdAndDateTraitementAfter(Long campagneId, LocalDate date);
}
