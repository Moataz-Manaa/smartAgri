package com.example.backend.repository;

import com.example.backend.entity.Culture;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CultureRepository extends JpaRepository<Culture, Long> {
    boolean existsByNomNormalise(String nomNormalise);

    boolean existsByNomNormaliseAndIdNot(String nomNormalise, Long id);
}
