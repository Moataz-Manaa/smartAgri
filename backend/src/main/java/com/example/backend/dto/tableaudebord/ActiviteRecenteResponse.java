package com.example.backend.dto.tableaudebord;

import com.example.backend.enums.TypeActivite;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ActiviteRecenteResponse {
    private Long id;
    private TypeActivite type;
    private LocalDate date;
    private Long plantationId;
    private Long parcelleId;
    private String parcelleCode;
    private String parcelleNom;
    private String campagneNom;
    private String description;
}
