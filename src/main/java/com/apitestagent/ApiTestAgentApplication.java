package com.apitestagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.apitestagent.config.AgentStorageProperties;
import com.apitestagent.config.AnalysisProperties;
import com.apitestagent.config.FlywayCompatibilityProperties;

@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
@EnableConfigurationProperties({AgentStorageProperties.class, AnalysisProperties.class, FlywayCompatibilityProperties.class})
public class ApiTestAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiTestAgentApplication.class, args);
    }
}
