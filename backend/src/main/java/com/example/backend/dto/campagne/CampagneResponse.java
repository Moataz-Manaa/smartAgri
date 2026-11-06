package com.example.backend.dto.campagne;

import com.example.backend.enums.StatutCampagne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CampagneResponse {
    private Long id;
    private String nom;
    private String description;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private StatutCampagne statut;
    private List<Long> parcelleIds;
    private List<ParcelleCampagneResponse> parcelles;
}
