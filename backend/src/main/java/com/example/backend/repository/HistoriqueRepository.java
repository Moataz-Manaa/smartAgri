package com.example.backend.repository;

import com.example.backend.dto.historique.ActiviteResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.enums.TypeActivite;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class HistoriqueRepository {
    private final JdbcTemplate jdbc;

    // UNION ALL conserve les activités, même si deux tables utilisent le même identifiant.
    // Aucun historique dupliqué à enregistrer : on lit les cinq tables existantes.
    private static final String ACTIVITES = """
        SELECT id, plantation_id, auteur_id, 'OBSERVATION' AS type,
            date_observation AS date_activite, description,
            CAST(NULL AS VARCHAR(100)) AS nom_produit,
            CAST(NULL AS NUMERIC(15,3)) AS quantite, CAST(NULL AS VARCHAR(20)) AS unite
        FROM observations WHERE plantation_id = ?
        UNION ALL
        SELECT id, plantation_id, auteur_id, 'IRRIGATION', date_irrigation,
            commentaire, NULL, volume, CAST(unite AS VARCHAR(20)) FROM irrigations WHERE plantation_id = ?
        UNION ALL
        SELECT id, plantation_id, auteur_id, 'FERTILISATION', date_fertilisation,
            commentaire, nom_produit, quantite, CAST(unite AS VARCHAR(20)) FROM fertilisations WHERE plantation_id = ?
        UNION ALL
        SELECT id, plantation_id, auteur_id, 'TRAITEMENT', date_traitement,
            commentaire, nom_produit, quantite, CAST(unite AS VARCHAR(20)) FROM traitements WHERE plantation_id = ?
        UNION ALL
        SELECT id, plantation_id, auteur_id, 'RECOLTE', date_recolte,
            commentaire, NULL, quantite, CAST(unite AS VARCHAR(20)) FROM recoltes WHERE plantation_id = ?
        """;

    public PageResponse<ActiviteResponse> consulterPage(Long plantationId, TypeActivite type,
            LocalDate dateDebut, LocalDate dateFin, Pageable pagination) {
        List<Object> parametres = new ArrayList<>();
        for (int i = 0; i < 5; i++) parametres.add(plantationId);
        String filtre = " WHERE 1 = 1";
        if (type != null) {
            filtre += " AND a.type = ?";
            parametres.add(type.name());
        }
        if (dateDebut != null) {
            filtre += " AND a.date_activite >= ?";
            parametres.add(java.sql.Date.valueOf(dateDebut));
        }
        if (dateFin != null) {
            filtre += " AND a.date_activite <= ?";
            parametres.add(java.sql.Date.valueOf(dateFin));
        }
        String source = " FROM (" + ACTIVITES + ") a";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + source + filtre,
                Long.class, parametres.toArray());
        parametres.add(pagination.getPageSize());
        parametres.add(pagination.getOffset());
        List<ActiviteResponse> contenu = jdbc.query(
                "SELECT a.*, u.nom, u.prenom" + source
                + " JOIN utilisateurs u ON u.id = a.auteur_id" + filtre
                + " ORDER BY a.date_activite DESC, a.type ASC, a.id DESC LIMIT ? OFFSET ?",
                (ligne, numero) -> new ActiviteResponse(ligne.getLong("id"),
                        TypeActivite.valueOf(ligne.getString("type")),
                        ligne.getDate("date_activite").toLocalDate(), ligne.getString("description"),
                        ligne.getString("nom_produit"), ligne.getBigDecimal("quantite"),
                        ligne.getString("unite"), ligne.getString("nom"), ligne.getString("prenom")),
                parametres.toArray());
        int totalPages = (int) Math.ceil((double) total / pagination.getPageSize());
        return new PageResponse<>(contenu, pagination.getPageNumber(), pagination.getPageSize(), total, totalPages);
    }
}
