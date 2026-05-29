package com.ama.jobmate.common.oauth;

import com.ama.jobmate.entity.Member;
import com.ama.jobmate.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // registrationId를 세션에서 꺼내거나 attributes로 판별
        String fakeEmail = resolveEmail(oAuth2User);

        try {
            if (fakeEmail != null) {
                Optional<Member> memberOpt = memberRepository.findByEmail(fakeEmail);
                if (memberOpt.isPresent()) {
                    Member member = memberOpt.get();
                    HttpSession session = request.getSession();
                    session.setAttribute("memberId", member.getId());
                    session.setAttribute("memberName", member.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        response.sendRedirect("/dashboard");
    }

    private String resolveEmail(OAuth2User oAuth2User) {
        // 카카오: "properties" 조건 제거, "id"만으로 판별
        if (oAuth2User.getAttributes().containsKey("id")) {
            KakaoOAuth2UserInfo info = new KakaoOAuth2UserInfo(oAuth2User.getAttributes());
            return "kakao_" + info.getId() + "@jobmate.com";
        }

        // 네이버: attributes에 "response" 키 존재
        if (oAuth2User.getAttributes().containsKey("response")) {
            NaverOAuth2UserInfo info = new NaverOAuth2UserInfo(oAuth2User.getAttributes());
            return "naver_" + info.getId() + "@jobmate.com";
        }

        // 구글: attributes에 "sub" 키 존재
        if (oAuth2User.getAttributes().containsKey("sub")) {
            GoogleOAuth2UserInfo info = new GoogleOAuth2UserInfo(oAuth2User.getAttributes());
            return info.getEmail() != null
                    ? info.getEmail()
                    : "google_" + info.getId() + "@jobmate.com";
        }

        return null;
    }
}