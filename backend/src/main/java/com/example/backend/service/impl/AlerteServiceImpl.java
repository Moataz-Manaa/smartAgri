package com.example.backend.service.impl;

import com.example.backend.config.AlerteRegles;
import com.example.backend.dto.alerte.*;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.entity.Alerte;
import com.example.backend.entity.Exploitation;
import com.example.backend.entity.LectureAlerte;
import com.example.backend.entity.Parcelle;
import com.example.backend.enums.StatutAlerte;
import com.example.backend.repository.*;
import com.example.backend.service.AlerteService;
import com.example.backend.service.AutorisationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlerteServiceImpl implements AlerteService {
    private final AlerteRepository alerteRepository;
    private final LectureAlerteRepository lectureAlerteRepository;
    private final AlerteConsultationRepository consultationRepository;
    private final ParcelleRepository parcelleRepository;
    private final AutorisationService autorisationService;
    private final AlerteRegles regles;
    private final Clock meteoHorloge;

    @Override
    public PageResponse<AlerteResponse> consulterPage(AlerteFiltreRequest filtre, Pageable pagination) {
        String keycloakId = autorisationService.obtenirKeycloakId();
        verifierFiltre(filtre);
        verifierParcelle(filtre.getParcelleId());
        if (pagination.getOffset() > Integer.MAX_VALUE - pagination.getPageSize()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La page demandée dépasse la limite de pagination");
        }
        Instant maintenant = meteoHorloge.instant();
        Page<Alerte> page = consultationRepository.consulterPage(filtre, autorisationService.estAdministrateur(),
                keycloakId, maintenant, pagination);
        List<Long> ids = new ArrayList<>();
        for (Alerte alerte : page.getContent()) ids.add(alerte.getId());
        Map<Long, Instant> lectures = new HashMap<>();
        if (!ids.isEmpty()) {
            for (LectureAlerte lecture : lectureAlerteRepository.findByKeycloakIdAndAlerte_IdIn(keycloakId, ids)) {
                lectures.put(lecture.getAlerte().getId(), lecture.getDateLecture());
            }
        }
        List<AlerteResponse> contenu = new ArrayList<>();
        for (Alerte alerte : page.getContent()) contenu.add(convertir(alerte, lectures.get(alerte.getId()), maintenant));
        return new PageResponse<>(contenu, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public AlerteResponse consulterParId(Long id) {
        String keycloakId = autorisationService.obtenirKeycloakId();
        verifierId(id);
        Alerte alerte = alerteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte introuvable"));
        autorisationService.verifierAccesParcelle(alerte.getParcelle().getId());
        Instant dateLecture = lectureAlerteRepository.findByAlerte_IdAndKeycloakId(id, keycloakId)
                .map(LectureAlerte::getDateLecture).orElse(null);
        return convertir(alerte, dateLecture, meteoHorloge.instant());
    }

    @Override
    @Transactional
    public AlerteResponse marquerLue(Long id) {
        String keycloakId = autorisationService.obtenirKeycloakId();
        verifierId(id);
        // Ce verrou court rend deux marquages simultanés idempotents.
        Alerte alerte = alerteRepository.findPourLecture(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte introuvable"));
        autorisationService.verifierAccesParcelle(alerte.getParcelle().getId());
        LectureAlerte lecture = lectureAlerteRepository.findByAlerte_IdAndKeycloakId(id, keycloakId).orElse(null);
        Instant maintenant = meteoHorloge.instant();
        if (lecture == null) {
            // Une précision commune à Java, PostgreSQL et au navigateur stabilise la réponse aux répétitions.
            lecture = LectureAlerte.builder().alerte(alerte).keycloakId(keycloakId)
                    .dateLecture(maintenant.truncatedTo(ChronoUnit.MILLIS)).build();
            lectureAlerteRepository.save(lecture);
        }
        return convertir(alerte, lecture.getDateLecture(), maintenant);
    }

    @Override
    public CompteurAlertesResponse consulterCompteur(Long parcelleId) {
        String keycloakId = autorisationService.obtenirKeycloakId();
        verifierParcelle(parcelleId);
        Instant maintenant = meteoHorloge.instant();
        long total = consultationRepository.compterActivesNonLues(autorisationService.estAdministrateur(),
                keycloakId, parcelleId, maintenant);
        return new CompteurAlertesResponse(total, maintenant);
    }

    private void verifierId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant invalide");
    }

    private void verifierParcelle(Long parcelleId) {
        if (parcelleId == null) return;
        verifierId(parcelleId);
        if (!parcelleRepository.existsById(parcelleId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcelle introuvable");
        }
        autorisationService.verifierAccesParcelle(parcelleId);
    }

    private void verifierFiltre(AlerteFiltreRequest filtre) {
        if (filtre.getDateDebut() != null && filtre.getDateFin() != null
                && filtre.getDateDebut().isAfter(filtre.getDateFin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de début doit précéder ou égaler la date de fin");
        }
        verifierDate(filtre.getDateDebut());
        verifierDate(filtre.getDateFin());
    }

    private void verifierDate(LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les dates doivent être comprises entre les années 1 et 9999");
        }
    }

    private boolean memeCoordonnee(Double premiere, Double seconde) {
        return premiere != null && seconde != null && premiere.doubleValue() == seconde.doubleValue();
    }

    private AlerteResponse convertir(Alerte alerte, Instant dateLecture, Instant maintenant) {
        Parcelle parcelle = alerte.getParcelle();
        Exploitation exploitation = parcelle.getExploitation();
        boolean pointIdentique = memeCoordonnee(alerte.getLatitude(), exploitation.getLatitude())
                && memeCoordonnee(alerte.getLongitude(), exploitation.getLongitude());
        StatutAlerte statut = alerte.getStatut();
        String motif = alerte.getMotifCloture();
        if (statut == StatutAlerte.ACTIVE && (!alerte.getDatePrevision().isAfter(maintenant) || !pointIdentique)) {
            statut = StatutAlerte.EXPIREE;
            motif = !pointIdentique ? "Le point météo de l'exploitation a changé" : "Échéance météo passée";
        }
        boolean recentes = !alerte.getDateRecuperationMeteo().isAfter(maintenant)
                && maintenant.isBefore(alerte.getDateRecuperationMeteo().plusSeconds(regles.getDonneesMaxSecondes()));
        return AlerteResponse.builder().id(alerte.getId()).parcelleId(parcelle.getId()).parcelleCode(parcelle.getCode())
                .parcelleNom(parcelle.getNom()).exploitationId(exploitation.getId()).exploitationNom(exploitation.getNom())
                .type(alerte.getType()).niveau(alerte.getNiveau()).statut(statut).message(alerte.getMessage())
                .valeur(alerte.getValeur()).seuil(alerte.getSeuil()).unite(alerte.getUnite())
                .latitude(alerte.getLatitude()).longitude(alerte.getLongitude()).source("OpenWeather")
                .datePrevision(alerte.getDatePrevision()).debutPeriode(alerte.getDebutPeriode())
                .dateDetection(alerte.getDateDetection()).derniereVerification(alerte.getDerniereVerification())
                .dateRecuperationMeteo(alerte.getDateRecuperationMeteo()).dateCloture(alerte.getDateCloture())
                .motifCloture(motif).donneesRecentes(recentes).lue(dateLecture != null).dateLecture(dateLecture).build();
    }
}
