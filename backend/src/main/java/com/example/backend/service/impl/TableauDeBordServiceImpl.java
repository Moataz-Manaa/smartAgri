package com.example.backend.service.impl;

import com.example.backend.dto.tableaudebord.*;
import com.example.backend.repository.CampagneRepository;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.repository.TableauDeBordRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.TableauDeBordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableauDeBordServiceImpl implements TableauDeBordService {
    private final TableauDeBordRepository repository;
    private final AutorisationService autorisation;
    private final ParcelleRepository parcelles;
    private final CampagneRepository campagnes;
    private final Clock meteoHorloge;

    @Override
    public SyntheseResponse consulterSynthese(TableauDeBordFiltreRequest filtre) {
        verifierFiltre(filtre);
        return repository.consulterSynthese(filtre, autorisation.estAdministrateur(), autorisation.obtenirKeycloakId());
    }

    @Override
    public List<ActiviteRecenteResponse> consulterActivites(TableauDeBordFiltreRequest filtre) {
        verifierFiltre(filtre);
        return repository.consulterActivites(filtre, autorisation.estAdministrateur(), autorisation.obtenirKeycloakId());
    }

    @Override
    public AlertesTableauDeBordResponse consulterAlertes(TableauDeBordFiltreRequest filtre) {
        verifierFiltre(filtre);
        return repository.consulterAlertes(filtre, autorisation.estAdministrateur(),
                autorisation.obtenirKeycloakId(), meteoHorloge.instant());
    }

    private void verifierFiltre(TableauDeBordFiltreRequest filtre) {
        // L'intercepteur vérifie aussi le compte Keycloak à chaque appel, sans cache d'autorisation.
        autorisation.obtenirKeycloakId();
        autorisation.obtenirRole();
        LocalDate aujourdHui = LocalDate.ofInstant(meteoHorloge.instant(), ZoneOffset.UTC);
        if (filtre.getDateDebut() == null) filtre.setDateDebut(aujourdHui.minusDays(29));
        if (filtre.getDateFin() == null) filtre.setDateFin(aujourdHui);
        if (filtre.getDateDebut().isAfter(filtre.getDateFin())
                || filtre.getDateDebut().getYear() < 1 || filtre.getDateFin().getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La période doit être valide, entre les années 1 et 9999");
        }
        if (filtre.getParcelleId() != null) {
            if (!parcelles.existsById(filtre.getParcelleId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable");
            }
            autorisation.verifierAccesParcelle(filtre.getParcelleId());
        }
        if (filtre.getCampagneId() != null) {
            if (!campagnes.existsById(filtre.getCampagneId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable");
            }
            autorisation.verifierAccesCampagne(filtre.getCampagneId());
        }
    }
}
