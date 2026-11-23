package com.example.backend.service.impl;

import com.example.backend.config.AlerteRegles;
import com.example.backend.dto.meteo.DonneesMeteoResponse;
import com.example.backend.dto.meteo.MeteoResponse;
import com.example.backend.dto.meteo.PrevisionMeteoResponse;
import com.example.backend.entity.Alerte;
import com.example.backend.entity.Exploitation;
import com.example.backend.entity.Parcelle;
import com.example.backend.enums.NiveauAlerte;
import com.example.backend.enums.StatutAlerte;
import com.example.backend.enums.StatutMeteo;
import com.example.backend.enums.TypeAlerte;
import com.example.backend.repository.AlerteRepository;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.service.AlerteCalculService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class AlerteCalculServiceImpl implements AlerteCalculService {
    private final AlerteRepository alerteRepository;
    private final ParcelleRepository parcelleRepository;
    private final AlerteRegles regles;
    private final Clock meteoHorloge;

    @Override
    public void expirer() {
        alerteRepository.expirer(meteoHorloge.instant(), StatutAlerte.ACTIVE, StatutAlerte.EXPIREE);
    }

    @Override
    public void evaluer(Long parcelleId, MeteoResponse meteo) {
        // Le verrou couvre seulement les écritures ; l'appel météo a déjà été effectué.
        Parcelle parcelle = parcelleRepository.findPourModification(parcelleId).orElse(null);
        if (parcelle == null) return;
        Instant maintenant = meteoHorloge.instant();
        Exploitation exploitation = parcelle.getExploitation();
        for (Alerte alerte : alerteRepository.findByParcelle_IdAndStatut(parcelleId, StatutAlerte.ACTIVE)) {
            if (!memeCoordonnee(alerte.getLatitude(), exploitation.getLatitude())
                    || !memeCoordonnee(alerte.getLongitude(), exploitation.getLongitude())) {
                cloturer(alerte, StatutAlerte.EXPIREE, maintenant, "Le point météo de l'exploitation a changé");
            } else if (!alerte.getDatePrevision().isAfter(maintenant)) {
                cloturer(alerte, StatutAlerte.EXPIREE, maintenant, "Échéance météo passée");
            }
        }
        if (meteo == null || meteo.getStatut() != StatutMeteo.DISPONIBLE
                || !Objects.equals(parcelleId, meteo.getParcelleId())
                || !Objects.equals(exploitation.getId(), meteo.getExploitationId())
                || !memeCoordonnee(exploitation.getLatitude(), meteo.getLatitude())
                || !memeCoordonnee(exploitation.getLongitude(), meteo.getLongitude())
                || meteo.getLatitude() == null || meteo.getLongitude() == null) return;
        DonneesMeteoResponse donnees = meteo.getDonnees();
        if (donnees == null || donnees.getRecupereesLe() == null || donnees.getPrevisions() == null
                || donnees.getRecupereesLe().isAfter(maintenant)
                || !maintenant.isBefore(donnees.getRecupereesLe().plusSeconds(regles.getDonneesMaxSecondes()))) return;

        Instant limite = maintenant.plusSeconds(regles.getHorizonHeures() * 3600L);
        for (PrevisionMeteoResponse prevision : donnees.getPrevisions()) {
            if (prevision == null || prevision.getDatePrevision() == null
                    || !prevision.getDatePrevision().isAfter(maintenant)
                    || prevision.getDatePrevision().isAfter(limite)) continue;
            Double temperature = prevision.getTemperatureCelsius();
            if (temperature != null && Double.isFinite(temperature) && temperature >= -150 && temperature <= 100) {
                appliquer(parcelle, donnees, prevision, TypeAlerte.CHALEUR, temperature,
                        regles.getChaleurCelsius(), regles.getChaleurCritiqueCelsius(), maintenant);
                appliquer(parcelle, donnees, prevision, TypeAlerte.GEL, temperature,
                        regles.getGelCelsius(), regles.getGelCritiqueCelsius(), maintenant);
            }
            Double pluie = prevision.getPluieTroisHeuresMillimetres();
            if (pluie != null && Double.isFinite(pluie) && pluie >= 0) {
                appliquer(parcelle, donnees, prevision, TypeAlerte.PLUIE_IMPORTANTE, pluie,
                        regles.getPluieTroisHeuresMm(), regles.getPluieCritiqueTroisHeuresMm(), maintenant);
            }
        }
    }

    private void appliquer(Parcelle parcelle, DonneesMeteoResponse donnees, PrevisionMeteoResponse prevision,
                           TypeAlerte type, double valeur, double seuil, double seuilCritique, Instant maintenant) {
        Exploitation exploitation = parcelle.getExploitation();
        Alerte alerte = alerteRepository.findByParcelle_IdAndTypeAndDatePrevisionAndLatitudeAndLongitude(
                parcelle.getId(), type, prevision.getDatePrevision(), exploitation.getLatitude(), exploitation.getLongitude())
                .orElse(null);
        // Une collecte arrivée en retard ne doit pas remplacer une source plus récente.
        if (alerte != null && donnees.getRecupereesLe().isBefore(alerte.getDateRecuperationMeteo())) return;
        boolean atteint = type == TypeAlerte.GEL ? valeur <= seuil : valeur >= seuil;
        if (alerte == null && !atteint) return;
        if (alerte == null) {
            alerte = Alerte.builder().parcelle(parcelle).type(type).datePrevision(prevision.getDatePrevision())
                    .debutPeriode(type == TypeAlerte.PLUIE_IMPORTANTE
                            ? prevision.getDatePrevision().minusSeconds(3 * 3600) : prevision.getDatePrevision())
                    .latitude(exploitation.getLatitude()).longitude(exploitation.getLongitude())
                    .dateDetection(maintenant).build();
        }
        boolean critique = type == TypeAlerte.GEL ? valeur <= seuilCritique : valeur >= seuilCritique;
        // Une alerte levée conserve son dernier niveau de déclenchement pour l'historique.
        if (atteint) alerte.setNiveau(critique ? NiveauAlerte.CRITIQUE : NiveauAlerte.AVERTISSEMENT);
        alerte.setValeur(valeur);
        alerte.setSeuil(atteint && critique ? seuilCritique : seuil);
        alerte.setUnite(type == TypeAlerte.PLUIE_IMPORTANTE ? "mm/3 h" : "°C");
        alerte.setDerniereVerification(maintenant);
        alerte.setDateRecuperationMeteo(donnees.getRecupereesLe());
        String mesure = type == TypeAlerte.PLUIE_IMPORTANTE ? "Pluie prévue sur trois heures" : "Température prévue";
        alerte.setMessage(mesure + " : " + valeur + " " + alerte.getUnite()
                + ". Seuil de suivi " + (type == TypeAlerte.GEL ? "≤ " : "≥ ") + alerte.getSeuil()
                + " " + alerte.getUnite() + ". Donnée estimée au point de l'exploitation.");
        if (atteint) {
            alerte.setStatut(StatutAlerte.ACTIVE);
            alerte.setDateCloture(null);
            alerte.setMotifCloture(null);
        } else {
            cloturer(alerte, StatutAlerte.LEVEE, maintenant, "La nouvelle prévision n'atteint plus le seuil de suivi");
        }
        alerteRepository.save(alerte);
    }

    private boolean memeCoordonnee(Double premiere, Double seconde) {
        return premiere != null && seconde != null && premiere.doubleValue() == seconde.doubleValue();
    }

    private void cloturer(Alerte alerte, StatutAlerte statut, Instant maintenant, String motif) {
        alerte.setStatut(statut);
        alerte.setDateCloture(maintenant);
        alerte.setMotifCloture(motif);
    }
}
