package com.ama.jobmate.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "member_profile")
public class MemberProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "member_id")
    private Member member;

    private String desiredJob;
    private String techStack;
    private String location;
    private Integer minSalary;
    private Integer maxSalary;

    private String careerLevel;
    private String employmentType;
    private String preferredWorkMode;

    // 거리 기반 매칭용 좌표 + 허용 거리
    private Double preferredLat;       // 선호 위치 위도
    private Double preferredLng;       // 선호 위치 경도
    private String preferredAddress;   // 선호 위치 주소 (표시용)
    private Integer maxDistanceKm;     // 허용 최대 거리 (km), 기본 20km

    // 매칭 가중치
    @Builder.Default
    private Integer weightJob = 40;

    @Builder.Default
    private Integer weightStack = 30;

    @Builder.Default
    private Integer weightLocation = 20;

    @Builder.Default
    private Integer weightSalary = 10;
}
