package com.example.backend.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class HorlogeMeteoTest extends Clock {
    private Instant maintenant = Instant.parse("2026-11-12T12:00:00Z");

    public void avancer(long secondes) { maintenant = maintenant.plusSeconds(secondes); }
    @Override public Instant instant() { return maintenant; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(maintenant, zone); }
}
