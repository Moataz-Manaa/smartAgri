package com.example.backend.service.impl;

import com.example.backend.dto.meteo.DonneesMeteoResponse;
import com.example.backend.dto.meteo.MeteoResponse;
import com.example.backend.dto.meteo.PrevisionMeteoResponse;
import com.example.backend.entity.Exploitation;
import com.example.backend.entity.Parcelle;
import com.example.backend.enums.StatutMeteo;
import com.example.backend.service.MeteoCollecteService;
import com.example.backend.service.OpenWeatherClient;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MeteoCollecteServiceImpl implements MeteoCollecteService {
    private final OpenWeatherClient openWeatherClient;
    private final Clock meteoHorloge;
    private final Map<String, EntreeCache> cache = new ConcurrentHashMap<>();
    @Value("${app.meteo.cache-secondes:900}")
    private long cacheSecondes = 900;
    @Value("${app.meteo.secours-secondes:7200}")
    private long secoursSecondes = 7200;
    @Value("${app.meteo.attente-apres-echec-secondes:60}")
    private long attenteApresEchecSecondes = 60;

    @Override
    public MeteoResponse collecter(Parcelle parcelle) {
        Exploitation exploitation = parcelle.getExploitation();
        Double latitude = exploitation.getLatitude();
        Double longitude = exploitation.getLongitude();
        if (latitude == null || longitude == null) {
            return reponse(parcelle, StatutMeteo.LOCALISATION_MANQUANTE,
                    "Localisation météo non renseignée. L'administrateur peut compléter les deux coordonnées.", null);
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return reponse(parcelle, StatutMeteo.LOCALISATION_INVALIDE,
                    "Les coordonnées de l'exploitation doivent être corrigées par l'administrateur.", null);
        }
        // Plusieurs parcelles peuvent partager le même point météo.
        String cle = latitude + ":" + longitude;
        EntreeCache entree = obtenirDonnees(cle, latitude, longitude);
        Instant maintenant = meteoHorloge.instant();
        if (entree.donnees == null || !maintenant.isBefore(entree.donnees.getRecupereesLe().plusSeconds(secoursSecondes))) {
            return reponse(parcelle, StatutMeteo.INDISPONIBLE, entree.message, null);
        }
        ArrayList<PrevisionMeteoResponse> previsions = new ArrayList<>();
        for (PrevisionMeteoResponse prevision : entree.donnees.getPrevisions()) {
            if (prevision.getDatePrevision().isAfter(maintenant)) previsions.add(prevision);
        }
        if (previsions.isEmpty()) {
            return reponse(parcelle, StatutMeteo.INDISPONIBLE, "Aucune prévision météo à venir n'est disponible.", null);
        }
        DonneesMeteoResponse donnees = DonneesMeteoResponse.builder()
                .recupereesLe(entree.donnees.getRecupereesLe()).conditions(entree.donnees.getConditions())
                .decalageUtcSecondes(entree.donnees.getDecalageUtcSecondes())
                .previsions(java.util.List.copyOf(previsions)).build();
        if (entree.echec || !estFraiche(entree, maintenant)) {
            return reponse(parcelle, StatutMeteo.PERIMEE,
                    "Mise à jour météo indisponible. Les dernières données connues sont affichées avec leur date.", donnees);
        }
        return reponse(parcelle, StatutMeteo.DISPONIBLE,
                "Météo au point de l'exploitation. Prévisions par intervalles de trois heures.", donnees);
    }

    private EntreeCache obtenirDonnees(String cle, double latitude, double longitude) {
        EntreeCache entree = cache.get(cle);
        if (reutilisable(entree, meteoHorloge.instant())) return entree;
        // Éviter les appels simultanés identiques. Les lectures d'un cache valide ne prennent pas ce verrou.
        synchronized (cache) {
            entree = cache.get(cle);
            Instant maintenant = meteoHorloge.instant();
            if (reutilisable(entree, maintenant)) return entree;
            EntreeCache nouvelle;
            try {
                DonneesMeteoResponse donnees = openWeatherClient.consulter(latitude, longitude);
                nouvelle = new EntreeCache(donnees, meteoHorloge.instant().plusSeconds(cacheSecondes), false, "");
            } catch (ResponseStatusException exception) {
                nouvelle = new EntreeCache(entree == null ? null : entree.donnees,
                        meteoHorloge.instant().plusSeconds(attenteApresEchecSecondes), true, exception.getReason());
            }
            // Borne mémoire simple ; une entrée évincée sera rechargée à la prochaine consultation.
            if (cache.size() >= 1000 && !cache.containsKey(cle)) cache.remove(cache.keySet().iterator().next());
            cache.put(cle, nouvelle);
            return nouvelle;
        }
    }

    private boolean reutilisable(EntreeCache entree, Instant maintenant) {
        return entree != null && maintenant.isBefore(entree.prochaineTentative)
                && (entree.echec || estFraiche(entree, maintenant));
    }

    private boolean estFraiche(EntreeCache entree, Instant maintenant) {
        return entree.donnees != null
                && !maintenant.isBefore(entree.donnees.getRecupereesLe())
                && maintenant.isBefore(entree.donnees.getRecupereesLe().plusSeconds(cacheSecondes))
                && !entree.donnees.getConditions().getDateDonnees().isBefore(maintenant.minusSeconds(3 * 3600));
    }

    private MeteoResponse reponse(Parcelle parcelle, StatutMeteo statut, String message, DonneesMeteoResponse donnees) {
        Exploitation exploitation = parcelle.getExploitation();
        return MeteoResponse.builder().parcelleId(parcelle.getId()).exploitationId(exploitation.getId())
                .exploitationNom(exploitation.getNom()).latitude(exploitation.getLatitude()).longitude(exploitation.getLongitude())
                .statut(statut).message(message).donnees(donnees).build();
    }

    @AllArgsConstructor
    private static class EntreeCache {
        private final DonneesMeteoResponse donnees;
        private final Instant prochaineTentative;
        private final boolean echec;
        private final String message;
    }
}
