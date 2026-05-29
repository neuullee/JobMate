package com.ama.jobmate.common.config;

import com.ama.jobmate.common.oauth.CustomOAuth2UserService;
import com.ama.jobmate.common.oauth.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Value("${kakao.logout-redirect-uri}")
    private String kakaoLogoutRedirectUri;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // ── 공개 URL ──
                        .requestMatchers(
                                "/",
                                "/login",
                                "/signup",
                                "/find-id",
                                "/find-password",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/fonts/**",
                                "/favicon.ico",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                // 회원가입·이메일 인증 API
                                "/api/members/signup",
                                "/api/members/check-email",
                                "/api/members/check-username",
                                "/api/members/verify-email",
                                "/api/members/send-verification",
                                "/api/members/find-id",
                                "/api/members/find-password",
                                "/api/members/reset-password",
                                // IP 기반 위치 (비로그인 허용)
                                "/api/location/ip"
                        ).permitAll()
                        // ── 관리자 전용 ──
                        .requestMatchers("/api/admin/**").authenticated()
                        // ── 나머지 전체 로그인 필요 ──
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2LoginSuccessHandler)
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl(
                                "https://kauth.kakao.com/oauth/logout"
                                + "?client_id=" + kakaoRestApiKey
                                + "&logout_redirect_uri=" + kakaoLogoutRedirectUri
                        )
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ForwardedHeaderFilter());
        bean.setOrder(0);
        return bean;
    }
}
