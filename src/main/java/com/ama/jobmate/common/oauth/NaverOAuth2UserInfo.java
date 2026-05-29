package com.ama.jobmate.common.oauth;

import java.util.Map;

public class NaverOAuth2UserInfo {

    private final Map<String, Object> attributes;

    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getResponse() {
        return (Map<String, Object>) attributes.get("response");
    }

    public String getId() {
        Map<String, Object> response = getResponse();
        if (response == null) return null;
        return (String) response.get("id");
    }

    public String getName() {
        Map<String, Object> response = getResponse();
        if (response == null) return "사용자";
        return (String) response.get("name");
    }

    public String getEmail() {
        Map<String, Object> response = getResponse();
        if (response == null) return null;
        return (String) response.get("email");
    }
}