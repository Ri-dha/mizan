package iq.mizan.market.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;

import iq.mizan.market.WorldRateProperties;
import iq.mizan.market.feed.FrankfurterClient.DayRate;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FrankfurterClientTest {

    private static final String URL = "https://frankfurter.example";

    private static final String CURRENCIES = """
            [{"iso_code": "EUR", "type": "currency"}, {"iso_code": "IRR", "type": "currency"},
             {"iso_code": "USD", "type": "currency"}, {"iso_code": "IQD", "type": "currency"},
             {"iso_code": "BTC", "type": "crypto"}, {"iso_code": "XAU", "type": "metal"}]
            """;

    private static final String RATES = """
            [{"date": "2026-11-20", "base": "USD", "quote": "EUR", "rate": 0.91003},
             {"date": "2026-11-20", "base": "USD", "quote": "IRR", "rate": 1936039},
             {"date": "2026-11-20", "base": "USD", "quote": "IQD", "rate": 1516.86},
             {"date": "2026-11-20", "base": "USD", "quote": "BTC", "rate": 0.0000105},
             {"date": "2026-11-20", "base": "USD", "quote": "XAU", "rate": 0.00024}]
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final FrankfurterClient client = new FrankfurterClient(builder, new WorldRateProperties(URL, "-", "UTC", 365));

    @Test
    void keepsRealCurrenciesButNotTheDollarOrTheDinar() {
        server.expect(requestTo(URL + "/v2/currencies")).andRespond(withSuccess(CURRENCIES, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/v2/rates?base=USD&from=2026-11-15")).andRespond(withSuccess(RATES, MediaType.APPLICATION_JSON));

        assertThat(client.since(LocalDate.parse("2026-11-15"))).containsExactly(
                new DayRate("EUR", LocalDate.parse("2026-11-20"), new BigDecimal("0.91003")),
                new DayRate("IRR", LocalDate.parse("2026-11-20"), new BigDecimal("1936039")));
        server.verify();
    }

    @Test
    void anEmptyCurrencyListIsAFailureNotAnEmptyAnswer() {
        server.expect(requestTo(URL + "/v2/currencies")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.since(LocalDate.parse("2026-11-15"))).isInstanceOf(IllegalStateException.class);
    }
}
