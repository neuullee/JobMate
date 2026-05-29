package com.ama.jobmate.dto;

import lombok.*;

@Getter @Setter
public class ProfileSetupRequest {
    private String desiredJob;
    private String careerLevel;    // 경력 (신입 / 주니어 / 미들 / 시니어)
    private String techStack;
    private String location;
    private Integer minSalary;
    private Integer maxSalary;

    // 거리 기반 매칭용 필드
    private Double preferredLat;
    private Double preferredLng;
    private String preferredAddress;
    private Integer maxDistanceKm;

    private Integer weightJob      = 40;
    private Integer weightStack    = 30;
    private Integer weightLocation = 20;
    private Integer weightSalary   = 10;
}
