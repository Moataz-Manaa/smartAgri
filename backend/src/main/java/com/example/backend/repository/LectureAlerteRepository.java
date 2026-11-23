package com.example.backend.repository;

import com.example.backend.entity.LectureAlerte;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LectureAlerteRepository extends JpaRepository<LectureAlerte, Long> {
    Optional<LectureAlerte> findByAlerte_IdAndKeycloakId(Long alerteId, String keycloakId);
    List<LectureAlerte> findByKeycloakIdAndAlerte_IdIn(String keycloakId, List<Long> alerteIds);
}
