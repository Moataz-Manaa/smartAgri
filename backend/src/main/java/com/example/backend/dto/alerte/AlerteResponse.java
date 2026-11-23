package com.example.backend.dto.alerte;

import com.example.backend.enums.NiveauAlerte;
import com.example.backend.enums.StatutAlerte;
import com.example.backend.enums.TypeAlerte;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

@Getter
@Builder
public class AlerteResponse {
    private Long id;
    private Long parcelleId;
    private String parcelleCode;
    private String parcelleNom;
    private Long exploitationId;
    private String exploitationNom;
    private TypeAlerte type;
    private NiveauAlerte niveau;
    private StatutAlerte statut;
    private String message;
    private Double valeur;
    private Double seuil;
    private String unite;
    private Double latitude;
    private Double longitude;
    private String source;
    private Instant datePrevision;
    private Instant debutPeriode;
    private Instant dateDetection;
    private Instant derniereVerification;
    private Instant dateRecuperationMeteo;
    private Instant dateCloture;
    private String motifCloture;
    private boolean donneesRecentes;
    private boolean lue;
    private Instant dateLecture;
}
