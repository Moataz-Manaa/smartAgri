package com.example.backend.repository;

import com.example.backend.entity.Affectation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AffectationRepository extends JpaRepository<Affectation, Long> {
    @Query("select count(a) from Affectation a where a.utilisateur.keycloakId = :keycloakId " +
            "and a.parcelle in (select p from Campagne c join c.parcelles p where c.id = :campagneId)")
    long compterAccesCampagne(@Param("keycloakId") String keycloakId, @Param("campagneId") Long campagneId);
    Page<Affectation> findByParcelle_Id(Long parcelleId, Pageable pagination);

    boolean existsByUtilisateur_IdAndParcelle_Id(Long utilisateurId, Long parcelleId);

    boolean existsByUtilisateur_KeycloakIdAndParcelle_Id(String keycloakId, Long parcelleId);

    boolean existsByUtilisateur_KeycloakIdAndParcelle_Exploitation_Id(
            String keycloakId, Long exploitationId);

    List<Affectation> findByParcelle_IdOrderByIdDesc(Long parcelleId);

    List<Affectation> findAllByOrderByIdDesc();
}
