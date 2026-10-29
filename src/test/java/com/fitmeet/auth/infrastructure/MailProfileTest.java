package com.fitmeet.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class MailProfileTest {
    private StandardEnvironment environment(String profile) throws Exception {
        var environment = new StandardEnvironment();
        var loader = new YamlPropertySourceLoader();
        for (var source : loader.load("base", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        if (profile != null) {
            for (var source : loader.load(profile, new ClassPathResource("application-" + profile + ".yml"))) {
                environment.getPropertySources().addFirst(source);
            }
        }
        return environment;
    }
    @Test void productionDefaultsRequireTlsAndFiniteTimeouts() throws Exception {
        var environment = environment(null);
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.connectiontimeout")).isEqualTo("5000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.timeout")).isEqualTo("5000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.writetimeout")).isEqualTo("5000");
    }
    @Test void mailpitProfilesOverrideInheritedProductionTlsRequirement() throws Exception {
        for (String profile : new String[]{"local", "integration"}) {
            var environment = environment(profile);
            assertThat(environment.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("false");
            assertThat(environment.getProperty("spring.mail.properties.mail.smtp.auth")).isEqualTo("false");
            assertThat(environment.getProperty("app.mail.from")).endsWith("@shockgraph.local");
        }
    }
}
