package com.example.backend;

import com.example.backend.dto.meteo.*;
import com.example.backend.entity.Exploitation;
import com.example.backend.entity.Parcelle;
import com.example.backend.enums.StatutMeteo;
import com.example.backend.repository.ParcelleRepository;
import com.example.backend.service.AutorisationService;
import com.example.backend.service.OpenWeatherClient;
import com.example.backend.service.impl.MeteoServiceImpl;
import com.example.backend.service.impl.MeteoCollecteServiceImpl;
import com.example.backend.support.HorlogeMeteoTest;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeteoCacheTests {
    ParcelleRepository parcelles;
    AutorisationService autorisation;
    OpenWeatherClient fournisseur;
    HorlogeMeteoTest horloge;
    MeteoServiceImpl service;
    MeteoCollecteServiceImpl collecte;
    Exploitation exploitation;

    @BeforeEach void preparer() {
        parcelles = mock(ParcelleRepository.class);
        autorisation = mock(AutorisationService.class);
        fournisseur = mock(OpenWeatherClient.class);
        horloge = new HorlogeMeteoTest();
        collecte = new MeteoCollecteServiceImpl(fournisseur, horloge);
        service = new MeteoServiceImpl(parcelles, autorisation, collecte);
        exploitation = Exploitation.builder().id(1L).nom("Ferme").latitude(34.74).longitude(10.76).build();
        when(parcelles.findAvecExploitation(1L)).thenReturn(Optional.of(
                Parcelle.builder().id(1L).exploitation(exploitation).build()));
        when(parcelles.findAvecExploitation(2L)).thenReturn(Optional.of(
                Parcelle.builder().id(2L).exploitation(exploitation).build()));
        when(fournisseur.consulter(anyDouble(), anyDouble())).thenAnswer(invocation -> donnees());
    }

    DonneesMeteoResponse donnees() {
        return DonneesMeteoResponse.builder().recupereesLe(horloge.instant()).decalageUtcSecondes(3600)
                .conditions(ConditionsMeteoResponse.builder().dateDonnees(horloge.instant()).temperatureCelsius(22.0).build())
                .previsions(List.of(PrevisionMeteoResponse.builder().datePrevision(horloge.instant().plusSeconds(10800)).build()))
                .build();
    }

    @Test void partagerLeCacheSansPartagerLesIdentifiantsDesParcelles() {
        assertThat(service.consulterParParcelle(1L).getParcelleId()).isEqualTo(1L);
        assertThat(service.consulterParParcelle(2L).getParcelleId()).isEqualTo(2L);
        verify(fournisseur, times(1)).consulter(34.74, 10.76);
        verify(autorisation).verifierAccesParcelle(2L);
    }

    @Test void partagerLeCacheEntreLaTacheInterneEtLApiSansContournerLesDroits() {
        collecte.collecter(parcelles.findAvecExploitation(1L).orElseThrow());
        verifyNoInteractions(autorisation);
        service.consulterParParcelle(2L);
        verify(autorisation).verifierAccesParcelle(2L);
        verify(fournisseur, times(1)).consulter(anyDouble(), anyDouble());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(autorisation).verifierAccesParcelle(2L);
        assertThatThrownBy(() -> service.consulterParParcelle(2L)).isInstanceOf(ResponseStatusException.class);
        verify(fournisseur, times(1)).consulter(anyDouble(), anyDouble());
    }

    @Test void rechargerApresModificationDesCoordonnees() {
        service.consulterParParcelle(1L);
        exploitation.setLatitude(35.0);
        assertThat(service.consulterParParcelle(1L).getLatitude()).isEqualTo(35.0);
        verify(fournisseur).consulter(35.0, 10.76);
    }

    @Test void rechargerApresExpirationDuCache() {
        service.consulterParParcelle(1L);
        horloge.avancer(899);
        service.consulterParParcelle(1L);
        verify(fournisseur, times(1)).consulter(anyDouble(), anyDouble());
        horloge.avancer(1);
        assertThat(service.consulterParParcelle(1L).getDonnees().getRecupereesLe()).isEqualTo(horloge.instant());
        verify(fournisseur, times(2)).consulter(anyDouble(), anyDouble());
    }

    @Test void conserverLesDernieresDonneesAvecUnStatutPerimeEtLimiterLesNouvellesTentatives() {
        var premiere = service.consulterParParcelle(1L);
        horloge.avancer(901);
        when(fournisseur.consulter(anyDouble(), anyDouble())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Panne"));
        var resultat = service.consulterParParcelle(1L);
        assertThat(resultat.getStatut()).isEqualTo(StatutMeteo.PERIMEE);
        assertThat(resultat.getDonnees().getRecupereesLe()).isEqualTo(premiere.getDonnees().getRecupereesLe());
        service.consulterParParcelle(2L);
        verify(fournisseur, times(2)).consulter(anyDouble(), anyDouble());
    }

    @Test void nePlusExposerLesDonneesApresDeuxHeuresDeSecours() {
        service.consulterParParcelle(1L);
        horloge.avancer(7200);
        when(fournisseur.consulter(anyDouble(), anyDouble())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Panne"));
        var resultat = service.consulterParParcelle(1L);
        assertThat(resultat.getStatut()).isEqualTo(StatutMeteo.INDISPONIBLE);
        assertThat(resultat.getDonnees()).isNull();
    }

    @Test void recupererApresUnePanneSansDoublerLesAppelsPendantUneMinute() {
        when(fournisseur.consulter(anyDouble(), anyDouble())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Quota"));
        assertThat(service.consulterParParcelle(1L).getDonnees()).isNull();
        horloge.avancer(59);
        assertThat(service.consulterParParcelle(1L).getStatut()).isEqualTo(StatutMeteo.INDISPONIBLE);
        verify(fournisseur, times(1)).consulter(anyDouble(), anyDouble());
        doAnswer(invocation -> donnees()).when(fournisseur).consulter(anyDouble(), anyDouble());
        horloge.avancer(1);
        assertThat(service.consulterParParcelle(1L).getStatut()).isEqualTo(StatutMeteo.DISPONIBLE);
    }

    @Test void verifierLesDroitsMemeAvecUnCacheValide() {
        service.consulterParParcelle(1L);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(autorisation).verifierAccesParcelle(1L);
        assertThatThrownBy(() -> service.consulterParParcelle(1L)).isInstanceOf(ResponseStatusException.class);
        verify(fournisseur, times(1)).consulter(anyDouble(), anyDouble());
    }

    @Test void retirerLesPrevisionsDejaPassees() {
        when(fournisseur.consulter(anyDouble(), anyDouble())).thenReturn(DonneesMeteoResponse.builder()
                .recupereesLe(horloge.instant()).decalageUtcSecondes(3600)
                .conditions(ConditionsMeteoResponse.builder().dateDonnees(horloge.instant()).build())
                .previsions(List.of(PrevisionMeteoResponse.builder().datePrevision(horloge.instant().plusSeconds(30)).build(),
                        PrevisionMeteoResponse.builder().datePrevision(horloge.instant().plusSeconds(10800)).build())).build());
        service.consulterParParcelle(1L);
        horloge.avancer(60);
        assertThat(service.consulterParParcelle(1L).getDonnees().getPrevisions()).hasSize(1);
    }
}
