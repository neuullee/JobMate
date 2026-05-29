package com.ama.jobmate;

import com.ama.jobmate.dto.LoginRequest;
import com.ama.jobmate.dto.ProfileSetupRequest;
import com.ama.jobmate.dto.SignupRequest;
import com.ama.jobmate.entity.Member;
import com.ama.jobmate.entity.MemberProfile;
import com.ama.jobmate.repository.MemberProfileRepository;
import com.ama.jobmate.repository.MemberRepository;
import com.ama.jobmate.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

//    MemberService 테스트 개요

//    중복 이메일 회원가입 실패
//    정상 회원가입 시 비밀번호 인코딩/기본값 저장
//    로그인 실패
//    프로필 최초 생성 시 기본 가중치 저장
//    비밀번호 재설정 시 8자 미만 실패

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberProfileRepository memberProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setId(1L);
        member.setEmail("user@test.com");
        member.setPassword("encoded-password");
        member.setName("테스터");
    }

    @Test
    void signup_shouldThrow_whenEmailAlreadyExists() {
        SignupRequest req = new SignupRequest();
        req.setEmail("user@test.com");
        req.setPassword("password123");
        req.setName("테스터");

        when(memberRepository.existsByEmail("user@test.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> memberService.signup(req)
        );

        assertEquals("이미 사용 중인 이메일입니다.", ex.getMessage());
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void signup_shouldEncodePasswordAndTrimFields_whenRequestIsValid() {
        SignupRequest req = new SignupRequest();
        req.setEmail("user@test.com");
        req.setPassword("password123");
        req.setName("테스터");
        req.setBirthDate("1990-01-02");
        req.setPhone(" 010-1111-2222 ");
        req.setAddress(" 서울시 강남구 ");
        req.setLatitude(37.5);
        req.setLongitude(127.0);

        when(memberRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member saved = memberService.signup(req);

        assertEquals("user@test.com", saved.getEmail());
        assertEquals("encoded-password", saved.getPassword());
        assertEquals("테스터", saved.getName());
        assertTrue(saved.isEmailVerified());
        assertEquals(LocalDate.of(1990, 1, 2), saved.getBirthDate());
        assertEquals("010-1111-2222", saved.getPhone());
        assertEquals("서울시 강남구", saved.getAddress());
        assertEquals(37.5, saved.getLatitude());
        assertEquals(127.0, saved.getLongitude());
    }

    @Test
    void login_shouldThrow_whenPasswordDoesNotMatch() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@test.com");
        req.setPassword("wrong-password");

        when(memberRepository.findByEmail("user@test.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> memberService.login(req)
        );

        assertEquals("이메일 또는 비밀번호가 틀렸습니다.", ex.getMessage());
    }

    @Test
    void setupProfile_shouldCreateProfileWithDefaultWeights_whenProfileDoesNotExist() {
        ProfileSetupRequest req = new ProfileSetupRequest();
        req.setDesiredJob("백엔드");
        req.setTechStack("Java,Spring");
        req.setLocation("서울");
        req.setMinSalary(4000);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberProfileRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        memberService.setupProfile(1L, req);

        ArgumentCaptor<MemberProfile> profileCaptor = ArgumentCaptor.forClass(MemberProfile.class);
        verify(memberProfileRepository).save(profileCaptor.capture());
        MemberProfile savedProfile = profileCaptor.getValue();

        assertEquals("백엔드", savedProfile.getDesiredJob());
        assertEquals("Java,Spring", savedProfile.getTechStack());
        assertEquals("서울", savedProfile.getLocation());
        assertEquals(4000, savedProfile.getMinSalary());
        assertEquals(40, savedProfile.getWeightJob());
        assertEquals(30, savedProfile.getWeightStack());
        assertEquals(20, savedProfile.getWeightLocation());
        assertEquals(10, savedProfile.getWeightSalary());

        verify(memberRepository).save(member);
        assertTrue(member.isProfileComplete());
    }

    @Test
    void resetPassword_shouldThrow_whenNewPasswordIsTooShort() {
        when(memberRepository.findByEmail("user@test.com")).thenReturn(Optional.of(member));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> memberService.resetPassword("user@test.com", "1234")
        );

        assertEquals("새 비밀번호는 8자 이상이어야 합니다.", ex.getMessage());
        verify(memberRepository, never()).save(any(Member.class));
    }
}
