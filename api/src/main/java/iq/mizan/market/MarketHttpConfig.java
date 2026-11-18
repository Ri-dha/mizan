package iq.mizan.market;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/** ETags for the price reads only: their answers are shared by everyone and change rarely. */
@Configuration
public class MarketHttpConfig {

    private static final String MARKET_READS = "/api/v1/market/*";

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> marketEtags() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns(MARKET_READS);
        return registration;
    }
}
