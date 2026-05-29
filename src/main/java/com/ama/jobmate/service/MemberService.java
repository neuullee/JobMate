package com.ama.jobmate.service;

import com.ama.jobmate.dto.LoginRequest;
import com.ama.jobmate.dto.ProfileSetupRequest;
import com.ama.jobmate.dto.SignupRequest;
import com.ama.jobmate.entity.Member;
import com.ama.jobmate.entity.MemberProfile;
import com.ama.jobmate.repository.MemberProfileRepository;
import com.ama.jobmate.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final PasswordEncoder passwordEncoder;

    // ── 회원가입 ──
    public Member signup(SignupRequest req) {
        if (memberRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        Member member = new Member();
        member.setEmail(req.getEmail());
        member.setPassword(passwordEncoder.encode(req.getPassword()));
        member.setName(req.getName());
        member.setEmailVerified(true);

        if (req.getBirthDate() != null && !req.getBirthDate().isBlank()) {
            try {
                member.setBirthDate(LocalDate.parse(req.getBirthDate()));
            } catch (Exception ignored) {
            }
        }

        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            member.setPhone(req.getPhone().trim());
        }

        // 주소 정보 저장
        if (req.getAddress() != null && !req.getAddress().isBlank()) {
            member.setAddress(req.getAddress().trim());
        }
        if (req.getLatitude() != null) {
            member.setLatitude(req.getLatitude());
        }
        if (req.getLongitude() != null) {
            member.setLongitude(req.getLongitude());
        }

        return memberRepository.save(member);
    }

    // ── 프로필 저장 ──
    @Transactional
    public void setupProfile(Long memberId, ProfileSetupRequest req) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음: id=" + memberId));

        MemberProfile profile = memberProfileRepository
                .findByMemberId(memberId)
                .orElse(null);

        if (profile == null) {
            profile = new MemberProfile();
            profile.setMember(member);
            profile.setWeightJob(40);
            profile.setWeightStack(30);
            profile.setWeightLocation(20);
            profile.setWeightSalary(10);
        }

        if (req.getDesiredJob() != null)  profile.setDesiredJob(req.getDesiredJob());
        if (req.getCareerLevel() != null) profile.setCareerLevel(req.getCareerLevel());
        if (req.getTechStack() != null)   profile.setTechStack(req.getTechStack());
        if (req.getLocation() != null)    profile.setLocation(req.getLocation());

        if (req.getMinSalary() != null)   profile.setMinSalary(req.getMinSalary());
        if (req.getMaxSalary() != null)   profile.setMaxSalary(req.getMaxSalary());

        if (req.getPreferredLat() != null)     profile.setPreferredLat(req.getPreferredLat());
        if (req.getPreferredLng() != null)     profile.setPreferredLng(req.getPreferredLng());
        if (req.getPreferredAddress() != null) profile.setPreferredAddress(req.getPreferredAddress());
        if (req.getMaxDistanceKm() != null)    profile.setMaxDistanceKm(req.getMaxDistanceKm());

        if (req.getWeightJob() != null)      profile.setWeightJob(req.getWeightJob());
        if (req.getWeightStack() != null)    profile.setWeightStack(req.getWeightStack());
        if (req.getWeightLocation() != null) profile.setWeightLocation(req.getWeightLocation());
        if (req.getWeightSalary() != null)   profile.setWeightSalary(req.getWeightSalary());

        memberProfileRepository.save(profile);

        member.setProfileComplete(true);
        memberRepository.save(member);
    }

    // ── 로그인 ──
    public Member login(LoginRequest req) {
        Member member = memberRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 틀렸습니다."));

        if (!passwordEncoder.matches(req.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 틀렸습니다.");
        }

        return member;
    }

    // ── 비밀번호 변경 ──
    // ── 비밀번호 변경 ──
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음"));

        // 소셜 로그인 유저(OAUTH_xxx)는 현재 비밀번호 검증 스킵
        String storedPassword = member.getPassword();
        boolean isSocialUser = storedPassword != null && storedPassword.startsWith("OAUTH_");

        if (!isSocialUser && !passwordEncoder.matches(currentPassword, storedPassword)) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }

        validateNewPassword(newPassword);
        member.setPassword(passwordEncoder.encode(newPassword));
        memberRepository.save(member);
    }

    // ── 비밀번호 재설정 ──
    public void resetPassword(String email, String newPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입된 이메일이 아닙니다."));

        validateNewPassword(newPassword);
        member.setPassword(passwordEncoder.encode(newPassword));
        memberRepository.save(member);
    }

    // ── 아이디(이메일) 찾기 ──
    public String findEmailForRecovery(String name, String phone, String birthDate) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해주세요.");
        }

        String trimmedName = name.trim();
        String trimmedPhone = phone == null ? "" : phone.trim();
        String trimmedBirthDate = birthDate == null ? "" : birthDate.trim();

        if (trimmedPhone.isBlank() && trimmedBirthDate.isBlank()) {
            throw new IllegalArgumentException("전화번호 또는 생년월일 중 하나는 입력해주세요.");
        }

        if (!trimmedPhone.isBlank()) {
            return memberRepository.findFirstByNameAndPhone(trimmedName, trimmedPhone)
                    .map(Member::getEmail)
                    .orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보를 찾을 수 없습니다."));
        }

        try {
            LocalDate birth = LocalDate.parse(trimmedBirthDate);
            return memberRepository.findFirstByNameAndBirthDate(trimmedName, birth)
                    .map(Member::getEmail)
                    .orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보를 찾을 수 없습니다."));
        } catch (Exception e) {
            throw new IllegalArgumentException("생년월일 형식이 올바르지 않습니다.");
        }
    }

    // ── 이메일 존재 여부 확인 ──
    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    // ── 생년월일/전화번호/기본주소 업데이트 ──
    public void updatePersonalInfo(Long memberId, String birthDate, String phone, String address, Double latitude, Double longitude) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음"));

        if (birthDate != null && !birthDate.isBlank()) {
            try {
                member.setBirthDate(LocalDate.parse(birthDate));
            } catch (Exception ignored) {
            }
        }

        if (phone != null) {
            member.setPhone(phone.trim());
        }

        if (address != null) {
            String trimmedAddress = address.trim();
            member.setAddress(trimmedAddress.isBlank() ? null : trimmedAddress);
        }

        member.setLatitude(latitude);
        member.setLongitude(longitude);

        memberRepository.save(member);
    }

    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("새 비밀번호는 8자 이상이어야 합니다.");
        }
    }
}