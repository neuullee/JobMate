package com.ama.jobmate.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(name = "provider")
    private String provider; // null = 일반 가입, "google"/"kakao" = 소셜

    private String nickname;

    private boolean emailVerified = false;

    private LocalDate birthDate;

    private String phone;

    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean profileComplete = false;

    // 기본 주소
    private String address;

    // 기본 주소 좌표
    private Double latitude;
    private Double longitude;
}