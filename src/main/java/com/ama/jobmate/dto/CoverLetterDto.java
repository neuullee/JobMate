package com.ama.jobmate.dto;

import com.ama.jobmate.entity.CoverLetter;
import lombok.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CoverLetterDto {

    /** 자소서 작성/수정 요청 DTO */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private String title;
        private String companyName;
        private String jobTitle;
        private String content;
    }

    /** 자소서 응답 DTO (목록 + 상세) */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long memberId;
        private String title;
        private String companyName;
        private String jobTitle;
        private String content;
        private String aiFeedback;
        private String createdAt;
        private String updatedAt;

        public static Response from(CoverLetter cl) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return Response.builder()
                    .id(cl.getId())
                    .memberId(cl.getMemberId())
                    .title(cl.getTitle())
                    .companyName(cl.getCompanyName())
                    .jobTitle(cl.getJobTitle())
                    .content(cl.getContent())
                    .aiFeedback(cl.getAiFeedback())
                    .createdAt(cl.getCreatedAt() != null ? cl.getCreatedAt().format(fmt) : "")
                    .updatedAt(cl.getUpdatedAt() != null ? cl.getUpdatedAt().format(fmt) : "")
                    .build();
        }
    }

    /** AI 피드백 응답 DTO */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FeedbackResponse {
        private String feedback;
    }
}
