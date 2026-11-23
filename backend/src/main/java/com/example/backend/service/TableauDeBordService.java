package com.example.backend.service;

import com.example.backend.dto.tableaudebord.*;
import java.util.List;

public interface TableauDeBordService {
    SyntheseResponse consulterSynthese(TableauDeBordFiltreRequest filtre);
    List<ActiviteRecenteResponse> consulterActivites(TableauDeBordFiltreRequest filtre);
    AlertesTableauDeBordResponse consulterAlertes(TableauDeBordFiltreRequest filtre);
}
