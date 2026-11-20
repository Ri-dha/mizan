package iq.mizan.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** ADR obs-5: VictoriaMetrics scrapes without a login, and finds every series the alert rules use. */
class PrometheusEndpointTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Test
    void servesTheSeriesTheAlertsReadWithoutALogin() throws Exception {
        api.get("/api/v1/market/quotes", null);

        MvcTestResult scrape = api.get("/actuator/prometheus", null);

        assertThat(scrape).hasStatus(HttpStatus.OK);
        String body = scrape.getResponse().getContentAsString();
        assertThat(body)
                .contains("http_server_requests_seconds_bucket{")
                .contains("jvm_memory_used_bytes{")
                .contains("jvm_memory_max_bytes{")
                .contains("hikaricp_connections_pending{")
                .contains("mizan_iqwealth_key_refused")
                .contains("mizan_iqwealth_paused")
                .contains("mizan_iqwealth_remaining_requests")
                .contains("application=\"mizan\"");
    }

    @Test
    void theOtherActuatorEndpointsStillNeedALogin() {
        assertThat(api.get("/actuator/metrics", null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
