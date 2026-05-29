package com.ama.jobmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequest {

    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    // 생년월일 (yyyy-MM-dd)
    private String birthDate;

    // 전화번호
    private String phone;

    // ✅ 추가
    private String address;
    private Double latitude;
    private Double longitude;
}