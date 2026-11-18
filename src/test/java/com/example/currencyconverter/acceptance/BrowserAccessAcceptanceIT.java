package com.example.currencyconverter.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Browser access")
class BrowserAccessAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    @Nested
    @DisplayName("Must accept browser requests only from the configured frontend origins")
    class MustAcceptBrowserRequestsOnlyFromConfiguredFrontendOrigins {

        @Test
        @DisplayName("The one where the configured frontend origin http://localhost:5173 is allowed")
        void configuredFrontendOriginIsAllowed() {
            MvcTestResult result = mvc.options().uri("/api/convert")
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "POST")
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).hasHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        }

        @ParameterizedTest(name = "The one where {0} is refused by the CORS policy")
        @ValueSource(strings = {"https://evil.example", "http://localhost:3000"})
        void disallowedOriginsAreRefused(String origin) {
            MvcTestResult result = mvc.options().uri("/api/convert")
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", "POST")
                    .exchange();

            assertThat(result).hasStatus(403);
        }

        @Test
        @DisplayName("The one where there is no Origin header (curl, server-to-server) — not affected by CORS")
        void noOriginHeaderIsNotAffectedByCors() {
            MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

            assertThat(result).hasStatusOk();
        }
    }
}
