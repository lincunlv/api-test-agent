package com.apitestagent.config;

import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Configuration
public class FlywayCompatibilityConfiguration {

    @Bean(initMethod = "migrate")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Flyway flyway(DataSource dataSource, FlywayCompatibilityProperties properties) {
        FluentConfiguration configuration = Flyway.configure().dataSource(dataSource);
        if (StringUtils.hasText(properties.getEncoding())) {
            configuration.encoding(properties.getEncoding().trim());
        }
        configuration.baselineOnMigrate(properties.isBaselineOnMigrate());
        List<String> locations = properties.getLocations();
        if (!CollectionUtils.isEmpty(locations)) {
            configuration.locations(locations.toArray(new String[0]));
        }
        return configuration.load();
    }
}