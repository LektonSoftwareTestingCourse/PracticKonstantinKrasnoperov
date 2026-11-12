package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.config.SwitchProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class SwitchFixtures {
    private SwitchFixtures() { }
    static SwitchProperties properties() {
        return new SwitchProperties("1", Map.of("400000", "ISS001"), "http://authorization", "http://logger",
                "http://acquirer", new SwitchProperties.HttpProperties(100, 100, 100),
                new SwitchProperties.RetryProperties(1, List.of(1L)), null);
    }
    static AuthorizationRequest request() {
        return new AuthorizationRequest("0100", "000001", "4000000000000002", "000000", new BigDecimal("100"),
                "643", Instant.parse("2026-06-30T12:00:00Z"), "TERM0001", "POS", "MERCH0000000001",
                "5411", "ACQ001", null);
    }
}
