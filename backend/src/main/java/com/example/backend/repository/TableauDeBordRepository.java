package com.example.backend.repository;

import com.example.backend.dto.tableaudebord.*;
import com.example.backend.enums.TypeActivite;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TableauDeBordRepository {
    private final JdbcTemplate jdbc;

    // Une seule ligne par activité. UNION ALL conserve les identifiants communs à plusieurs tables.
    private static final String ACTIVITES = """
        SELECT id, plantation_id, 'OBSERVATION' AS type, date_observation AS date_activite,
            description FROM observations
        UNION ALL SELECT id, plantation_id, 'IRRIGATION', date_irrigation, commentaire FROM irrigations
        UNION ALL SELECT id, plantation_id, 'FERTILISATION', date_fertilisation, commentaire FROM fertilisations
        UNION ALL SELECT id, plantation_id, 'TRAITEMENT', date_traitement, commentaire FROM traitements
        UNION ALL SELECT id, plantation_id, 'RECOLTE', date_recolte, commentaire FROM recoltes
        """;

    private String source(TableauDeBordFiltreRequest filtre, boolean administrateur, String keycloakId,
                          List<Object> params) {
        String sql = "WITH parcelles_visibles AS (SELECT p.* FROM parcelles p WHERE 1 = 1";
        if (!administrateur) {
            sql += " AND EXISTS (SELECT 1 FROM affectations af JOIN utilisateurs u ON u.id = af.utilisateur_id"
                    + " WHERE af.parcelle_id = p.id AND u.keycloak_id = ?)";
            params.add(keycloakId);
        }
        if (filtre.getParcelleId() != null) {
            sql += " AND p.id = ?";
            params.add(filtre.getParcelleId());
        }
        if (filtre.getCampagneId() != null) {
            sql += " AND EXISTS (SELECT 1 FROM campagne_parcelles cp WHERE cp.parcelle_id = p.id AND cp.campagne_id = ?)";
            params.add(filtre.getCampagneId());
        }
        sql += "), campagnes_suivies AS (SELECT c.* FROM campagnes c WHERE c.date_debut <= ? AND c.date_fin >= ?";
        params.add(Date.valueOf(filtre.getDateFin()));
        params.add(Date.valueOf(filtre.getDateDebut()));
        if (filtre.getCampagneId() != null) {
            sql += " AND c.id = ?";
            params.add(filtre.getCampagneId());
        }
        sql += " AND EXISTS (SELECT 1 FROM campagne_parcelles cp JOIN parcelles_visibles pv ON pv.id = cp.parcelle_id"
                + " WHERE cp.campagne_id = c.id)), plantations_suivies AS ("
                + " SELECT pl.* FROM plantations pl JOIN parcelles_visibles pv ON pv.id = pl.parcelle_id"
                + " JOIN campagnes_suivies cs ON cs.id = pl.campagne_id) ";
        return sql;
    }

    private void ajouterPeriode(TableauDeBordFiltreRequest filtre, List<Object> params) {
        params.add(Date.valueOf(filtre.getDateDebut()));
        params.add(Date.valueOf(filtre.getDateFin()));
    }

    public SyntheseResponse consulterSynthese(TableauDeBordFiltreRequest filtre, boolean administrateur, String keycloakId) {
        List<Object> params = new ArrayList<>();
        String sql = source(filtre, administrateur, keycloakId, params) + """
            SELECT (SELECT COUNT(*) FROM parcelles_visibles) AS parcelles,
                (SELECT COUNT(*) FROM campagnes_suivies) AS campagnes,
                (SELECT COUNT(*) FROM plantations_suivies) AS plantations,
                (SELECT COUNT(*) FROM (
            """ + ACTIVITES + """
                ) a JOIN plantations_suivies pl ON pl.id = a.plantation_id
                    WHERE a.date_activite BETWEEN ? AND ?) AS activites,
                (SELECT COALESCE(SUM(i.volume_metres_cubes), 0) FROM irrigations i
                    JOIN plantations_suivies pl ON pl.id = i.plantation_id
                    WHERE i.date_irrigation BETWEEN ? AND ?) AS irrigation,
                (SELECT COALESCE(SUM(r.quantite_kilogrammes), 0) FROM recoltes r
                    JOIN plantations_suivies pl ON pl.id = r.plantation_id
                    WHERE r.date_recolte BETWEEN ? AND ?) AS recolte
            """;
        for (int i = 0; i < 3; i++) ajouterPeriode(filtre, params);
        return jdbc.queryForObject(sql, (ligne, numero) -> new SyntheseResponse(
                filtre.getDateDebut(), filtre.getDateFin(), ligne.getLong("parcelles"), ligne.getLong("campagnes"),
                ligne.getLong("plantations"), ligne.getLong("activites"), ligne.getBigDecimal("irrigation"),
                ligne.getBigDecimal("recolte")), params.toArray());
    }

    public List<ActiviteRecenteResponse> consulterActivites(TableauDeBordFiltreRequest filtre, boolean administrateur, String keycloakId) {
        List<Object> params = new ArrayList<>();
        String sql = source(filtre, administrateur, keycloakId, params) + """
            SELECT a.*, pl.parcelle_id, pv.code, pv.nom, cs.nom AS campagne_nom FROM (
            """ + ACTIVITES + """
            ) a JOIN plantations_suivies pl ON pl.id = a.plantation_id
                JOIN parcelles_visibles pv ON pv.id = pl.parcelle_id
                JOIN campagnes_suivies cs ON cs.id = pl.campagne_id
            WHERE a.date_activite BETWEEN ? AND ?
            ORDER BY a.date_activite DESC, a.type ASC, a.id DESC LIMIT 5
            """;
        ajouterPeriode(filtre, params);
        return jdbc.query(sql, (ligne, numero) -> new ActiviteRecenteResponse(ligne.getLong("id"),
                TypeActivite.valueOf(ligne.getString("type")), ligne.getDate("date_activite").toLocalDate(),
                ligne.getLong("plantation_id"), ligne.getLong("parcelle_id"), ligne.getString("code"),
                ligne.getString("nom"), ligne.getString("campagne_nom"), ligne.getString("description")), params.toArray());
    }

    public AlertesTableauDeBordResponse consulterAlertes(TableauDeBordFiltreRequest filtre, boolean administrateur,
                                                       String keycloakId, Instant maintenant) {
        List<Object> params = new ArrayList<>();
        String sql = source(filtre, administrateur, keycloakId, params) + """
            SELECT COUNT(*) AS actives, COALESCE(SUM(CASE WHEN NOT EXISTS
                (SELECT 1 FROM lectures_alertes l WHERE l.alerte_id = a.id AND l.keycloak_id = ?)
                THEN 1 ELSE 0 END), 0) AS non_lues
            FROM alertes a JOIN parcelles_visibles pv ON pv.id = a.parcelle_id
                JOIN exploitations e ON e.id = pv.exploitation_id
            WHERE a.statut = 'ACTIVE' AND a.date_prevision > ?
                AND a.latitude = e.latitude AND a.longitude = e.longitude
                AND a.date_prevision >= ? AND a.date_prevision < ?
            """;
        params.add(keycloakId);
        params.add(maintenant.atOffset(ZoneOffset.UTC));
        params.add(filtre.getDateDebut().atStartOfDay().atOffset(ZoneOffset.UTC));
        params.add(filtre.getDateFin().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
        return jdbc.queryForObject(sql, (ligne, numero) -> new AlertesTableauDeBordResponse(
                ligne.getLong("actives"), ligne.getLong("non_lues"), maintenant), params.toArray());
    }
}
