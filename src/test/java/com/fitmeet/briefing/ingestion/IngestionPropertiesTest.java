package com.fitmeet.briefing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.io.ClassPathResource;

class IngestionPropertiesTest {
    @Test
    void applicationYamlBindsTheInitialIssuerWithoutJavaTickerConstants() throws Exception {
        var source = new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yml")).get(0);
        var properties = new Binder(ConfigurationPropertySources.from(source))
                .bind("app.briefing.ingestion", Bindable.of(IngestionProperties.class)).get();
        assertThat(properties.validatedIssuers()).hasSize(1);
        assertThat(properties.validatedIssuers().get(0))
                .isEqualTo(new SecIssuer("CAMT", "0001109138",
                        java.util.Set.of("6-K", "6-K/A", "20-F", "20-F/A")));
    }
}
