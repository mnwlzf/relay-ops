package com.relayops.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void createsCorrelationIdWhenRequestContextIsEmpty() {
        ApiResponse<String> response = ApiResponse.of("ok");

        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.requestId()).isNotBlank();
    }
}
