package com.apitestagent.domain;

public class SkillBundle {

    private final String skillIndexContent;
    private final String referenceContent;

    public SkillBundle(String skillIndexContent, String referenceContent) {
        this.skillIndexContent = skillIndexContent;
        this.referenceContent = referenceContent;
    }

    public String getSkillIndexContent() {
        return skillIndexContent;
    }

    public String getReferenceContent() {
        return referenceContent;
    }
}
