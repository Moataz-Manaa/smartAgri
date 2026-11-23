package com.example.backend;

import com.example.backend.config.AlertePlanification;
import com.example.backend.config.AlerteRegles;
import com.example.backend.dto.meteo.*;
import com.example.backend.entity.Alerte;
import com.example.backend.entity.Exploitation;
import com.example.backend.entity.Parcelle;
import com.example.backend.enums.*;
import com.example.backend.repository.*;
import com.example.backend.service.*;
import com.example.backend.support.HorlogeMeteoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:smartagri-alertes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class AlerteCalculTests {
    @Autowired AlerteCalculService calcul;
    @Autowired AlerteGenerationService generation;
    @Autowired AlerteRepository alertes;
    @Autowired ParcelleRepository parcelles;
    @Autowired ExploitationRepository exploitations;
    @Autowired AlerteRegles regles;
    @Autowired ApplicationContext contexte;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MeteoCollecteService meteo;
    @MockitoBean Clock meteoHorloge;
    HorlogeMeteoTest horloge;
    Parcelle parcelle;
    Exploitation exploitation;
    Instant echeance;

    @BeforeEach void preparer() {
        alertes.deleteAll();
        parcelles.deleteAll();
        exploitations.deleteAll();
        horloge = new HorlogeMeteoTest();
        when(meteoHorloge.instant()).thenAnswer(invocation -> horloge.instant());
        regles.setChaleurCelsius(35);
        regles.setChaleurCritiqueCelsius(40);
        exploitation = exploitations.saveAndFlush(Exploitation.builder().code("EXP-ALERTE").nom("Ferme test")
                .latitude(34.74).longitude(10.76).build());
        parcelle = parcelles.saveAndFlush(Parcelle.builder().code("PAR-ALERTE").nom("Parcelle test")
                .superficie(5.0).exploitation(exploitation).build());
        echeance = horloge.instant().plusSeconds(3 * 3600);
    }

    MeteoResponse reponse(Double temperature, Double pluie) {
        return reponse(parcelle.getId(), StatutMeteo.DISPONIBLE,
                donnees(horloge.instant(), 3600, List.of(prevision(echeance, temperature, pluie))));
    }

    MeteoResponse reponse(Long parcelleId, StatutMeteo statut, DonneesMeteoResponse donnees) {
        return MeteoResponse.builder().parcelleId(parcelleId).exploitationId(exploitation.getId())
                .latitude(exploitation.getLatitude()).longitude(exploitation.getLongitude()).statut(statut)
                .donnees(donnees).build();
    }

    DonneesMeteoResponse donnees(Instant collecte, int decalage, List<PrevisionMeteoResponse> previsions) {
        return DonneesMeteoResponse.builder().recupereesLe(collecte).decalageUtcSecondes(decalage)
                .conditions(ConditionsMeteoResponse.builder().dateDonnees(horloge.instant())
                        .temperatureCelsius(50.0).pluieDerniereHeureMillimetres(100.0).build())
                .previsions(previsions).build();
    }

    PrevisionMeteoResponse prevision(Instant date, Double temperature, Double pluie) {
        return PrevisionMeteoResponse.builder().datePrevision(date).temperatureCelsius(temperature)
                .pluieTroisHeuresMillimetres(pluie).probabilitePrecipitationsPourcent(100.0).build();
    }

    Alerte unique() {
        assertThat(alertes.findAll()).hasSize(1);
        return alertes.findAll().getFirst();
    }

    @Test void appliquerLesBornesDeChaleurEtMettreAJourSansDoublon() {
        calcul.evaluer(parcelle.getId(), reponse(34.99, 0.0));
        assertThat(alertes.count()).isZero();
        calcul.evaluer(parcelle.getId(), reponse(35.0, 0.0));
        Long id = unique().getId();
        assertThat(unique().getNiveau()).isEqualTo(NiveauAlerte.AVERTISSEMENT);
        calcul.evaluer(parcelle.getId(), reponse(40.0, 0.0));
        assertThat(unique().getNiveau()).isEqualTo(NiveauAlerte.CRITIQUE);
        assertThat(unique().getSeuil()).isEqualTo(40);
        calcul.evaluer(parcelle.getId(), reponse(35.0, 0.0));
        assertThat(unique().getNiveau()).isEqualTo(NiveauAlerte.AVERTISSEMENT);
        calcul.evaluer(parcelle.getId(), reponse(34.99, 0.0));
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.LEVEE);
        assertThat(unique().getDateCloture()).isEqualTo(horloge.instant());
        calcul.evaluer(parcelle.getId(), reponse(35.0, 0.0));
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
        assertThat(unique().getDateCloture()).isNull();
        assertThat(unique().getId()).isEqualTo(id);
    }

    @Test void appliquerLesBornesInclusivesDuGel() {
        calcul.evaluer(parcelle.getId(), reponse(0.01, null));
        assertThat(alertes.count()).isZero();
        calcul.evaluer(parcelle.getId(), reponse(0.0, null));
        assertThat(unique().getType()).isEqualTo(TypeAlerte.GEL);
        assertThat(unique().getNiveau()).isEqualTo(NiveauAlerte.AVERTISSEMENT);
        calcul.evaluer(parcelle.getId(), reponse(-3.0, null));
        assertThat(unique().getNiveau()).isEqualTo(NiveauAlerte.CRITIQUE);
        assertThat(unique().getUnite()).isEqualTo("°C");
        assertThat(unique().getDebutPeriode()).isEqualTo(echeance);
    }

    @Test void comparerLaPluieDuCreneauSansUtiliserLaProbabiliteNiAdditionnerLesCreneaux() {
        MeteoResponse resultat = reponse(parcelle.getId(), StatutMeteo.DISPONIBLE,
                donnees(horloge.instant(), 3600, List.of(prevision(echeance, 20.0, 10.0),
                        prevision(echeance.plusSeconds(10800), 20.0, 10.0))));
        calcul.evaluer(parcelle.getId(), resultat);
        assertThat(alertes.count()).isZero();
        calcul.evaluer(parcelle.getId(), reponse(20.0, 20.0));
        assertThat(unique().getType()).isEqualTo(TypeAlerte.PLUIE_IMPORTANTE);
        assertThat(unique().getNiveau()).isEqualTo(NiveauAlerte.AVERTISSEMENT);
        assertThat(unique().getDebutPeriode()).isEqualTo(echeance.minusSeconds(10800));
        calcul.evaluer(parcelle.getId(), reponse(20.0, 40.0));
        assertThat(unique().getNiveau()).isEqualTo(NiveauAlerte.CRITIQUE);
        assertThat(unique().getUnite()).isEqualTo("mm/3 h");
    }

    @Test void ignorerLesDonneesAbsentesEtInvalidesSansLeverUneAlerte() {
        calcul.evaluer(parcelle.getId(), reponse(40.0, null));
        for (Double valeur : new Double[] {null, Double.NaN, Double.POSITIVE_INFINITY, 101.0}) {
            calcul.evaluer(parcelle.getId(), reponse(valeur, -1.0));
            assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
            assertThat(unique().getValeur()).isEqualTo(40);
        }
        calcul.evaluer(parcelle.getId(), reponse(null, Double.NaN));
        assertThat(alertes.count()).isEqualTo(1);
    }

    @Test void garderUneAlertePendantUnePanneOuUnStatutPerime() {
        calcul.evaluer(parcelle.getId(), reponse(40.0, null));
        for (StatutMeteo statut : new StatutMeteo[] {StatutMeteo.PERIMEE, StatutMeteo.INDISPONIBLE,
                StatutMeteo.LOCALISATION_MANQUANTE, StatutMeteo.LOCALISATION_INVALIDE}) {
            MeteoResponse resultat = reponse(parcelle.getId(), statut, reponse(20.0, null).getDonnees());
            calcul.evaluer(parcelle.getId(), resultat);
            assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
            assertThat(unique().getValeur()).isEqualTo(40);
        }
        calcul.evaluer(parcelle.getId(), null);
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
    }

    @Test void refuserUnStatutDisponibleAvecUneCollecteTropAncienneOuFuture() {
        MeteoResponse resultat = reponse(parcelle.getId(), StatutMeteo.DISPONIBLE,
                donnees(horloge.instant().minusSeconds(900), 3600, List.of(prevision(echeance, 40.0, null))));
        calcul.evaluer(parcelle.getId(), resultat);
        assertThat(alertes.count()).isZero();
        resultat = reponse(parcelle.getId(), StatutMeteo.DISPONIBLE,
                donnees(horloge.instant().plusSeconds(1), 3600, List.of(prevision(echeance, 40.0, null))));
        calcul.evaluer(parcelle.getId(), resultat);
        assertThat(alertes.count()).isZero();
        resultat = reponse(parcelle.getId(), StatutMeteo.DISPONIBLE,
                donnees(horloge.instant().minusSeconds(899), 3600, List.of(prevision(echeance, 40.0, null))));
        calcul.evaluer(parcelle.getId(), resultat);
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
    }

    @Test void appliquerUnHorizonDe24HeuresAvecUneBorneSuperieureIncluse() {
        MeteoResponse resultat = reponse(parcelle.getId(), StatutMeteo.DISPONIBLE,
                donnees(horloge.instant(), 3600, List.of(prevision(horloge.instant(), 40.0, null),
                        prevision(horloge.instant().minusSeconds(1), 40.0, null),
                        prevision(horloge.instant().plusSeconds(86400), 40.0, null),
                        prevision(horloge.instant().plusSeconds(86401), 40.0, null))));
        calcul.evaluer(parcelle.getId(), resultat);
        assertThat(unique().getDatePrevision()).isEqualTo(horloge.instant().plusSeconds(86400));
    }

    @Test void expirerExactementALEcheanceMemeSansMeteo() {
        calcul.evaluer(parcelle.getId(), reponse(40.0, null));
        horloge.avancer(10799);
        calcul.expirer();
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
        horloge.avancer(1);
        calcul.expirer();
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.EXPIREE);
        assertThat(unique().getMotifCloture()).contains("Échéance");
        verifyNoInteractions(meteo);
    }

    @Test void conserverLHistoriqueQuandLePointMeteoChange() {
        calcul.evaluer(parcelle.getId(), reponse(40.0, null));
        Long ancienne = unique().getId();
        exploitation.setLatitude(35.0);
        exploitations.saveAndFlush(exploitation);
        calcul.evaluer(parcelle.getId(), reponse(40.0, null));
        assertThat(alertes.findAll()).hasSize(2);
        assertThat(alertes.findById(ancienne).orElseThrow().getStatut()).isEqualTo(StatutAlerte.EXPIREE);
        assertThat(alertes.findByParcelle_IdAndStatut(parcelle.getId(), StatutAlerte.ACTIVE)).hasSize(1)
                .allSatisfy(alerte -> assertThat(alerte.getLatitude()).isEqualTo(35));
    }

    @Test void refuserUneCollecteDeLAncienPointEtExpirerApresRetraitDesCoordonnees() {
        MeteoResponse ancienne = reponse(40.0, null);
        calcul.evaluer(parcelle.getId(), ancienne);
        exploitation.setLatitude(null);
        exploitation.setLongitude(null);
        exploitations.saveAndFlush(exploitation);
        calcul.evaluer(parcelle.getId(), ancienne);
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.EXPIREE);
    }

    @Test void nePasRemplacerUneSourcePlusRecenteParUneReponseArriveeEnRetard() {
        MeteoResponse ancienne = reponse(20.0, null);
        horloge.avancer(1);
        calcul.evaluer(parcelle.getId(), reponse(40.0, null));
        calcul.evaluer(parcelle.getId(), ancienne);
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
        assertThat(unique().getValeur()).isEqualTo(40);
    }

    @Test void conserverLaMemeAlerteQuelQueSoitLeDecalageDAffichage() {
        MeteoResponse resultat = reponse(40.0, null);
        calcul.evaluer(parcelle.getId(), resultat);
        Long id = unique().getId();
        resultat = reponse(parcelle.getId(), StatutMeteo.DISPONIBLE,
                donnees(horloge.instant(), -19800, resultat.getDonnees().getPrevisions()));
        calcul.evaluer(parcelle.getId(), resultat);
        assertThat(unique().getId()).isEqualTo(id);
        assertThat(unique().getDatePrevision()).isEqualTo(echeance);
    }

    @Test void accepterDeuxCalculsConcurrentsSansDoublon() throws Exception {
        MeteoResponse resultat = reponse(40.0, 40.0);
        CountDownLatch depart = new CountDownLatch(1);
        try (var executants = Executors.newFixedThreadPool(2)) {
            var premier = executants.submit(() -> { depart.await(); calcul.evaluer(parcelle.getId(), resultat); return true; });
            var second = executants.submit(() -> { depart.await(); calcul.evaluer(parcelle.getId(), resultat); return true; });
            depart.countDown();
            assertThat(premier.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(alertes.findAll()).hasSize(2).extracting(Alerte::getType)
                .containsExactlyInAnyOrder(TypeAlerte.CHALEUR, TypeAlerte.PLUIE_IMPORTANTE);
    }

    @Test void faireRespecterLaContrainteUniqueParLaBase() {
        calcul.evaluer(parcelle.getId(), reponse(40.0, null));
        String colonnes = "parcelle_id,type,niveau,statut,message,valeur,seuil,unite,latitude,longitude,"
                + "date_prevision,debut_periode,date_detection,derniere_verification,date_recuperation_meteo,version";
        assertThatThrownBy(() -> jdbc.update("insert into alertes (" + colonnes + ") select " + colonnes
                + " from alertes where id = ?", unique().getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(alertes.count()).isEqualTo(1);
    }

    @Test void poursuivreLeCalculDesAutresParcellesApresUnePanneEtSansTransactionReseau() {
        Parcelle seconde = parcelles.saveAndFlush(Parcelle.builder().code("PAR-ALERTE-2").nom("Seconde parcelle")
                .superficie(2.0).exploitation(exploitation).build());
        MeteoResponse resultat = reponse(seconde.getId(), StatutMeteo.DISPONIBLE, reponse(40.0, null).getDonnees());
        when(meteo.collecter(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Parcelle p = invocation.getArgument(0);
            if (p.getId().equals(parcelle.getId())) throw new IllegalStateException("Panne simulée");
            return resultat;
        });
        generation.generer();
        assertThat(unique().getParcelle().getId()).isEqualTo(seconde.getId());
    }

    @Test void appliquerLesSeuilsConfigures() {
        regles.setChaleurCelsius(32);
        regles.setChaleurCritiqueCelsius(38);
        calcul.evaluer(parcelle.getId(), reponse(38.0, null));
        assertThat(unique().getNiveau()).isEqualTo(NiveauAlerte.CRITIQUE);
        assertThat(unique().getSeuil()).isEqualTo(38);
    }

    @Test void nePasConfondreUnePrevisionAbsenteAvecUneDisparitionDuRisque() {
        calcul.evaluer(parcelle.getId(), reponse(40.0, null));
        calcul.evaluer(parcelle.getId(), reponse(parcelle.getId(), StatutMeteo.DISPONIBLE,
                donnees(horloge.instant(), 3600, List.of())));
        calcul.evaluer(parcelle.getId(), reponse(parcelle.getId(), StatutMeteo.DISPONIBLE, null));
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
        assertThat(unique().getValeur()).isEqualTo(40);
    }

    @Test void refuserUneCollecteDestineeAUneAutreParcelle() {
        calcul.evaluer(parcelle.getId(), reponse(parcelle.getId() + 100, StatutMeteo.DISPONIBLE,
                reponse(40.0, null).getDonnees()));
        assertThat(alertes.count()).isZero();
    }

    @Test void considererLesZerosPositifEtNegatifCommeLeMemePoint() {
        exploitation.setLatitude(0.0);
        exploitation.setLongitude(0.0);
        exploitations.saveAndFlush(exploitation);
        calcul.evaluer(parcelle.getId(), MeteoResponse.builder().parcelleId(parcelle.getId())
                .exploitationId(exploitation.getId()).latitude(-0.0).longitude(-0.0).statut(StatutMeteo.DISPONIBLE)
                .donnees(reponse(40.0, null).getDonnees()).build());
        assertThat(unique().getStatut()).isEqualTo(StatutAlerte.ACTIVE);
    }

    @Test void nePasDemarrerLaTachePlanifieeDansLesTests() {
        assertThat(contexte.getBeansOfType(AlertePlanification.class)).isEmpty();
    }
}
