package com.apitestagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.storage")
public class AgentStorageProperties {

    private String workspaceBaseDir = "workspace/agent_files";

    private String skillsBaseDir = "skills/interface-chain-analyzer";

    public String getWorkspaceBaseDir() {
        return workspaceBaseDir;
    }

    public void setWorkspaceBaseDir(String workspaceBaseDir) {
        this.workspaceBaseDir = workspaceBaseDir;
    }

    public String getSkillsBaseDir() {
        return skillsBaseDir;
    }

    public void setSkillsBaseDir(String skillsBaseDir) {
        this.skillsBaseDir = skillsBaseDir;
    }
}
