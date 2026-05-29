package com.ama.jobmate.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cover_letter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoverLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작성자 (member_id FK)
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 자소서 제목
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 지원 회사명
    @Column(name = "company_name", length = 100)
    private String companyName;

    // 지원 직무
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    // 자소서 본문
    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // AI 피드백 (Groq 응답)
    @Lob
    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    // 작성일시
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 수정일시
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
