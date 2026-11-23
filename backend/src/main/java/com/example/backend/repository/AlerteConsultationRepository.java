package com.example.backend.repository;

import com.example.backend.dto.alerte.AlerteFiltreRequest;
import com.example.backend.entity.Alerte;
import com.example.backend.enums.StatutAlerte;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AlerteConsultationRepository {
    private final EntityManager entityManager;

    // Une affectation partagée ne multiplie pas les résultats : l'accès utilise EXISTS.
    private static final String ACCES = " exists (select af.id from Affectation af where af.parcelle = p "
            + "and af.utilisateur.keycloakId = :keycloakId)";
    private static final String LECTURE = " exists (select l.id from LectureAlerte l where l.alerte = a "
            + "and l.keycloakId = :keycloakId)";
    private static final String ACTIVE = " (a.statut = :active and a.datePrevision > :maintenant "
            + "and e.latitude is not null and e.longitude is not null "
            + "and a.latitude = e.latitude and a.longitude = e.longitude)";
    private static final String EXPIREE = " (a.statut = :expiree or (a.statut = :active "
            + "and (a.datePrevision <= :maintenant or e.latitude is null or e.longitude is null "
            + "or a.latitude <> e.latitude or a.longitude <> e.longitude)))";

    public Page<Alerte> consulterPage(AlerteFiltreRequest filtre, boolean administrateur, String keycloakId,
                                      Instant maintenant, Pageable pagination) {
        Map<String, Object> parametres = new HashMap<>();
        String conditions = " where 1 = 1";
        if (!administrateur) {
            conditions += " and" + ACCES;
            parametres.put("keycloakId", keycloakId);
        }
        if (filtre.getParcelleId() != null) {
            conditions += " and p.id = :parcelleId";
            parametres.put("parcelleId", filtre.getParcelleId());
        }
        if (filtre.getType() != null) {
            conditions += " and a.type = :type";
            parametres.put("type", filtre.getType());
        }
        if (filtre.getNiveau() != null) {
            conditions += " and a.niveau = :niveau";
            parametres.put("niveau", filtre.getNiveau());
        }
        if (filtre.getStatut() == StatutAlerte.ACTIVE || filtre.getStatut() == StatutAlerte.EXPIREE) {
            conditions += " and" + (filtre.getStatut() == StatutAlerte.ACTIVE ? ACTIVE : EXPIREE);
            parametres.put("active", StatutAlerte.ACTIVE);
            parametres.put("maintenant", maintenant);
            if (filtre.getStatut() == StatutAlerte.EXPIREE) parametres.put("expiree", StatutAlerte.EXPIREE);
        } else if (filtre.getStatut() != null) {
            conditions += " and a.statut = :statut";
            parametres.put("statut", filtre.getStatut());
        }
        if (filtre.getLue() != null) {
            conditions += " and" + (filtre.getLue() ? LECTURE : " not" + LECTURE);
            parametres.put("keycloakId", keycloakId);
        }
        if (filtre.getDateDebut() != null) {
            conditions += " and a.datePrevision >= :debut";
            parametres.put("debut", filtre.getDateDebut().atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        if (filtre.getDateFin() != null) {
            conditions += " and a.datePrevision < :fin";
            parametres.put("fin", filtre.getDateFin().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        TypedQuery<Alerte> requete = entityManager.createQuery(
                "select a from Alerte a join fetch a.parcelle p join fetch p.exploitation e" + conditions
                        + " order by a.dateDetection desc, a.id desc", Alerte.class);
        TypedQuery<Long> comptage = entityManager.createQuery(
                "select count(a) from Alerte a join a.parcelle p join p.exploitation e" + conditions, Long.class);
        for (Map.Entry<String, Object> parametre : parametres.entrySet()) {
            requete.setParameter(parametre.getKey(), parametre.getValue());
            comptage.setParameter(parametre.getKey(), parametre.getValue());
        }
        long total = comptage.getSingleResult();
        List<Alerte> contenu = requete.setFirstResult((int) pagination.getOffset())
                .setMaxResults(pagination.getPageSize()).getResultList();
        return new PageImpl<>(contenu, pagination, total);
    }

    public long compterActivesNonLues(boolean administrateur, String keycloakId, Long parcelleId, Instant maintenant) {
        String conditions = " where" + ACTIVE + " and not" + LECTURE;
        if (!administrateur) conditions += " and" + ACCES;
        if (parcelleId != null) conditions += " and p.id = :parcelleId";
        TypedQuery<Long> requete = entityManager.createQuery(
                "select count(a) from Alerte a join a.parcelle p join p.exploitation e" + conditions, Long.class);
        requete.setParameter("active", StatutAlerte.ACTIVE);
        requete.setParameter("maintenant", maintenant);
        requete.setParameter("keycloakId", keycloakId);
        if (parcelleId != null) requete.setParameter("parcelleId", parcelleId);
        return requete.getSingleResult();
    }
}
