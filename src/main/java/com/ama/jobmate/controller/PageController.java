package com.ama.jobmate.controller;

import com.ama.jobmate.entity.Member;
import com.ama.jobmate.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final MemberRepository memberRepository;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @GetMapping("/find-id")
    public String findId() {
        return "find-id";
    }

    @GetMapping("/find-password")
    public String findPassword() {
        return "find-password";
    }

    /**
     * 대시보드: 신규 회원(profileComplete=false)이면 으로 리다이렉트
     */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return "redirect:/";

        Optional<Member> memberOpt = memberRepository.findById(memberId);
        if (memberOpt.isPresent() && !memberOpt.get().isProfileComplete()) {
            return "redirect:/profile-setup";
        }

        return "dashboard";
    }

    @GetMapping("/profile-setup")
    public String profileSetup() {
        return "profile-setup";
    }

    @GetMapping("/jobs")
    public String jobs() {
        return "jobs";
    }

    @GetMapping("/interview")
    public String interview() {
        return "interview";
    }

    @GetMapping("/interview-notes")
    public String interviewNotes() {
        return "interview-notes";
    }

    @GetMapping("/applications")
    public String applications() {
        return "applications";
    }

    @GetMapping("/job-detail")
    public String jobDetail() {
        return "job-detail";
    }

    @GetMapping("/community")
    public String community() {
        return "community";
    }

    @GetMapping("/account")
    public String account() {
        return "account";
    }

    @GetMapping("/community/{id}")
    public String communityDetail() {
        return "community-detail";
    }
}
