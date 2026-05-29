package com.ama.jobmate.controller;

import com.ama.jobmate.dto.LoginRequest;
import com.ama.jobmate.dto.ProfileSetupRequest;
import com.ama.jobmate.dto.SignupRequest;
import com.ama.jobmate.entity.Member;
import com.ama.jobmate.entity.MemberProfile;
import com.ama.jobmate.repository.MemberProfileRepository;
import com.ama.jobmate.repository.MemberRepository;
import com.ama.jobmate.service.EmailVerificationService;
import com.ama.jobmate.service.MemberService;
import com.ama.jobmate.service.NpsApiService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final NpsApiService npsApiService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    private final WebClient kakaoWebClient = WebClient.builder()
            .baseUrl("https://dapi.kakao.com")
            .build();

    // ── 회원가입 ──
    @PostMapping("/signup")
    public ResponseEntity<String> signup(
            @Valid @RequestBody SignupRequest req,
            HttpSession session) {
        Member member = memberService.signup(req);
        session.setAttribute("memberId", member.getId());
        session.setAttribute("memberName", member.getName());
        return ResponseEntity.ok("회원가입 성공");
    }

    // ── 프로필 저장 ──
    @PostMapping("/{memberId}/profile")
    public ResponseEntity<?> setupProfile(
            @PathVariable Long memberId,
            @RequestBody ProfileSetupRequest req,
            HttpSession session) {

        Object sessionIdObj = session.getAttribute("memberId");
        if (sessionIdObj == null) {
            log.warn("프로필 저장 시도: 세션 없음 (memberId={})", memberId);
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }

        Long sessionMemberId;
        if (sessionIdObj instanceof Integer) {
            sessionMemberId = ((Integer) sessionIdObj).longValue();
        } else if (sessionIdObj instanceof Long) {
            sessionMemberId = (Long) sessionIdObj;
        } else {
            sessionMemberId = Long.parseLong(sessionIdObj.toString());
        }

        if (!sessionMemberId.equals(memberId)) {
            log.warn("프로필 저장 권한 없음: session={}, path={}", sessionMemberId, memberId);
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "권한이 없습니다."));
        }

        try {
            memberService.setupProfile(memberId, req);
            log.info("프로필 저장 성공: memberId={}", memberId);
            return ResponseEntity.ok(Map.of("success", true, "message", "맞춤 정보 설정 완료"));
        } catch (Exception e) {
            log.error("프로필 저장 실패: memberId={}, error={}", memberId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "저장 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/{memberId}/profile")
    public ResponseEntity<?> getProfile(@PathVariable Long memberId) {
        Optional<MemberProfile> profile = memberProfileRepository.findByMemberId(memberId);
        if (profile.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(profile.get());
    }

    // ── 개인정보(생년월일/전화번호/기본주소) 업데이트 ──
    @PostMapping("/{memberId}/personal-info")
    public ResponseEntity<String> updatePersonalInfo(
            @PathVariable Long memberId,
            @RequestBody Map<String, Object> body,
            HttpSession session) {

        Long sessionId = (Long) session.getAttribute("memberId");
        if (sessionId == null || !sessionId.equals(memberId)) {
            return ResponseEntity.status(403).body("권한이 없습니다.");
        }

        String birthDate = body.get("birthDate") == null ? null : body.get("birthDate").toString();
        String phone = body.get("phone") == null ? null : body.get("phone").toString();
        String address = body.get("address") == null ? null : body.get("address").toString();

        Double latitude = null;
        Double longitude = null;

        try {
            if (body.get("latitude") != null && !body.get("latitude").toString().isBlank()) {
                latitude = Double.parseDouble(body.get("latitude").toString());
            }
            if (body.get("longitude") != null && !body.get("longitude").toString().isBlank()) {
                longitude = Double.parseDouble(body.get("longitude").toString());
            }
        } catch (Exception ignored) {
        }

        memberService.updatePersonalInfo(memberId, birthDate, phone, address, latitude, longitude);
        return ResponseEntity.ok("저장 완료");
    }

    // ── 로그인 ──
    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest req,
            HttpSession session) {
        Member member = memberService.login(req);
        session.setAttribute("memberId", member.getId());
        session.setAttribute("memberName", member.getName());
        return ResponseEntity.ok("로그인 성공: " + member.getName());
    }

    // ── 로그아웃 ──
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok("로그아웃 완료");
    }

    // ── 내 정보 ──
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음"));

        Map<String, Object> result = new HashMap<>();
        result.put("id", member.getId());
        result.put("name", member.getName());
        result.put("email", member.getEmail());
        result.put("nickname", member.getNickname() == null ? "" : member.getNickname());
        result.put("birthDate", member.getBirthDate() == null ? "" : member.getBirthDate().toString());
        result.put("phone", member.getPhone() == null ? "" : member.getPhone());
        result.put("address", member.getAddress() == null ? "" : member.getAddress());
        result.put("latitude", member.getLatitude());
        result.put("longitude", member.getLongitude());

        return ResponseEntity.ok(result);
    }

    // ── 이메일 인증 코드 발송 ──
    @PostMapping("/verify/send")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "이메일을 입력해주세요."
            ));
        }

        boolean checkDuplicate = Boolean.parseBoolean(body.getOrDefault("checkDuplicate", "false"));
        boolean checkExist = Boolean.parseBoolean(body.getOrDefault("checkExist", "false"));

        if (checkDuplicate && memberService.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "이미 사용 중인 이메일입니다."
            ));
        }

        if (checkExist && !memberService.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "가입된 이메일이 아닙니다."
            ));
        }

        try {
            emailVerificationService.sendVerificationCode(email);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "인증 코드를 발송했습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "메일 발송 실패: " + e.getMessage()
            ));
        }
    }

    // ── 인증 코드 검증 ──
    @PostMapping("/verify/check")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");

        boolean ok = emailVerificationService.verifyCode(email, code);
        if (ok) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "인증 완료"
            ));
        }

        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "인증 코드가 올바르지 않거나 만료되었습니다."
        ));
    }

    // ── 아이디(이메일) 찾기 ──
    @PostMapping("/find-id")
    public ResponseEntity<?> findId(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String phone = body.get("phone");
        String birthDate = body.get("birthDate");

        try {
            String email = memberService.findEmailForRecovery(name, phone, birthDate);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "email", maskEmail(email)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ── 비밀번호 재설정 ──
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String newPassword = body.get("newPassword");

        if (!emailVerificationService.consumeVerified(email)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "이메일 인증을 먼저 완료해주세요."
            ));
        }

        try {
            memberService.resetPassword(email, newPassword);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "비밀번호가 변경되었습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ── 비밀번호 변경 ──
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "로그인이 필요합니다."
            ));
        }

        try {
            memberService.changePassword(memberId, body.get("currentPassword"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "비밀번호가 변경되었습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ── 닉네임 변경 ──
    @PostMapping("/{id}/nickname")
    public ResponseEntity<?> updateNickname(@PathVariable Long id,
                                            @RequestBody Map<String, String> body,
                                            HttpSession session) {
        Long sessionId = (Long) session.getAttribute("memberId");
        if (sessionId == null || !sessionId.equals(id)) {
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "권한이 없습니다."
            ));
        }

        String nickname = body.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "닉네임을 입력해주세요."
            ));
        }

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음"));
        member.setNickname(nickname.trim());
        memberRepository.save(member);

        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── 회원 탈퇴 ──
    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, String> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "로그인이 필요합니다."
            ));
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음"));

        if (!passwordEncoder.matches(body.get("password"), member.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "비밀번호가 올바르지 않습니다."
            ));
        }

        session.invalidate();
        memberRepository.deleteById(memberId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── 지오코딩 ──
    @GetMapping("/geocode")
    public ResponseEntity<?> geocode(@RequestParam String address) {
        try {
            final String addr = address;

            Map response = kakaoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/address.json")
                            .queryParam("query", addr)
                            .queryParam("size", "1")
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                List documents = (List) response.get("documents");
                if (documents != null && !documents.isEmpty()) {
                    Map doc = (Map) documents.get(0);
                    Map result = new HashMap<>();
                    result.put("lat", Double.parseDouble((String) doc.get("y")));
                    result.put("lng", Double.parseDouble((String) doc.get("x")));

                    Map addressInfo = (Map) doc.get("address");
                    String addressName = addressInfo != null ? (String) addressInfo.get("address_name") : address;
                    result.put("address", addressName);
                    return ResponseEntity.ok(result);
                }
            }

            Map kwResponse = kakaoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", addr)
                            .queryParam("size", "1")
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (kwResponse != null) {
                List kwDocs = (List) kwResponse.get("documents");
                if (kwDocs != null && !kwDocs.isEmpty()) {
                    Map doc = (Map) kwDocs.get(0);
                    Map result = new HashMap<>();
                    result.put("lat", Double.parseDouble((String) doc.get("y")));
                    result.put("lng", Double.parseDouble((String) doc.get("x")));
                    result.put("address", doc.get("address_name"));
                    return ResponseEntity.ok(result);
                }
            }

            return ResponseEntity.ok(Map.of("lat", null, "lng", null));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("지오코딩 오류: " + e.getMessage());
        }
    }

    // ── 역지오코딩 ──
    @GetMapping("/reverse-geocode")
    public ResponseEntity<?> reverseGeocode(@RequestParam double lat, @RequestParam double lng) {
        try {
            Map response = kakaoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2address.json")
                            .queryParam("x", lng)
                            .queryParam("y", lat)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                List documents = (List) response.get("documents");
                if (documents != null && !documents.isEmpty()) {
                    Map doc = (Map) documents.get(0);
                    Map addressInfo = (Map) doc.get("address");
                    if (addressInfo != null) {
                        return ResponseEntity.ok(Map.of("address", (String) addressInfo.get("address_name")));
                    }
                }
            }

            return ResponseEntity.ok(Map.of("address", "위도 " + lat + ", 경도 " + lng));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("역지오코딩 오류: " + e.getMessage());
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf("@");
        if (atIndex <= 1) return email;

        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (local.length() <= 3) {
            return local.charAt(0) + "**" + domain;
        }

        return local.substring(0, 3) + "*".repeat(local.length() - 3) + domain;
    }
}