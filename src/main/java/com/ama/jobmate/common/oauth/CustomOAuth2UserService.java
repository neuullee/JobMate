package com.ama.jobmate.common.oauth;

import com.ama.jobmate.entity.Member;
import com.ama.jobmate.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String fakeEmail;
        String name;

        switch (registrationId) {
            case "kakao" -> {
                KakaoOAuth2UserInfo userInfo = new KakaoOAuth2UserInfo(oAuth2User.getAttributes());
                fakeEmail = "kakao_" + userInfo.getId() + "@jobmate.com";
                name = userInfo.getNickname();
            }
            case "naver" -> {
                NaverOAuth2UserInfo userInfo = new NaverOAuth2UserInfo(oAuth2User.getAttributes());
                fakeEmail = "naver_" + userInfo.getId() + "@jobmate.com";
                name = userInfo.getName();
            }
            case "google" -> {
                GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(oAuth2User.getAttributes());
                // 구글은 실제 이메일 사용 가능
                fakeEmail = userInfo.getEmail() != null
                        ? userInfo.getEmail()
                        : "google_" + userInfo.getId() + "@jobmate.com";
                name = userInfo.getName();
            }
            default -> throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인: " + registrationId);
        }

        Optional<Member> existing = memberRepository.findByEmail(fakeEmail);
        if (existing.isEmpty()) {
            Member member = new Member();
            member.setEmail(fakeEmail);
            member.setName(name);
            member.setPassword("OAUTH_" + registrationId);
            memberRepository.save(member);
        } else {
            Member member = existing.get();
            member.setName(name);
            memberRepository.save(member);
        }

        return oAuth2User;
    }
}