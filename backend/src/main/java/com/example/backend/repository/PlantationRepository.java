package com.example.backend.repository;

import com.example.backend.entity.Plantation;
import com.example.backend.enums.StatutPlantation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface PlantationRepository extends JpaRepository<Plantation, Long> {
    // L'affectation est vérifiée avant la pagination et le comptage.
    @Query("select p from Plantation p where (:parcelleId is null or p.parcelle.id = :parcelleId) " +
            "and (:campagneId is null or p.campagne.id = :campagneId) " +
            "and (:statut is null or p.statut = :statut) " +
            "and (:administrateur = true or exists (select a.id from Affectation a " +
            "where a.parcelle = p.parcelle and a.utilisateur.keycloakId = :keycloakId))")
    Page<Plantation> findPageAccessible(@Param("parcelleId") Long parcelleId,
            @Param("campagneId") Long campagneId, @Param("statut") StatutPlantation statut,
            @Param("administrateur") boolean administrateur, @Param("keycloakId") String keycloakId,
            Pageable pagination);

    boolean existsByVariete_Id(Long varieteId);
    List<Plantation> findByCampagne_Id(Long campagneId);
    boolean existsByParcelle_IdAndSuperficieGreaterThan(Long parcelleId, Double superficie);
}
