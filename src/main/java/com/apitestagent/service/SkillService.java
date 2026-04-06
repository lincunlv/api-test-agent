package com.apitestagent.service;

import com.apitestagent.config.AgentStorageProperties;
import com.apitestagent.domain.SkillBundle;
import com.apitestagent.domain.SkillType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class SkillService {

    private final AgentStorageProperties properties;

    public SkillService(AgentStorageProperties properties) {
        this.properties = properties;
    }

    public SkillBundle load(SkillType skillType) throws IOException {
        Path baseDir = Paths.get(properties.getSkillsBaseDir());
        String skillIndexContent = new String(Files.readAllBytes(baseDir.resolve("SKILL.md")), StandardCharsets.UTF_8);
        String referenceContent = new String(Files.readAllBytes(baseDir.resolve("references").resolve(skillType.getReferenceFile())), StandardCharsets.UTF_8);
        return new SkillBundle(skillIndexContent, referenceContent);
    }
}
