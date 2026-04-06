package com.apitestagent;

import com.apitestagent.config.AgentStorageProperties;
import com.apitestagent.config.AnalysisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentStorageProperties.class, AnalysisProperties.class})
public class ApiTestAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiTestAgentApplication.class, args);
    }
}
