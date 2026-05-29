package com.ama.jobmate.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;

    // email -> [code, expiryMillis]
    private final Map<String, long[]> codeStore = new ConcurrentHashMap<>();

    // 인증 완료된 이메일 임시 저장 (비밀번호 재설정용)
    // email -> verifiedExpiryMillis
    private final Map<String, Long> verifiedStore = new ConcurrentHashMap<>();

    private static final long CODE_EXPIRY_MS = 5 * 60 * 1000L;       // 5분
    private static final long VERIFIED_EXPIRY_MS = 10 * 60 * 1000L;  // 10분

    /** 인증코드 생성 후 메일 발송 */
    public void sendVerificationCode(String toEmail) throws Exception {
        String code = String.format("%06d", new Random().nextInt(1000000));
        long expiry = System.currentTimeMillis() + CODE_EXPIRY_MS;

        codeStore.put(toEmail, new long[]{Long.parseLong(code), expiry});
        verifiedStore.remove(toEmail); // 새 코드 발송 시 기존 인증완료 상태 제거

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject("[JobMate] 이메일 인증 코드");
        helper.setText(buildCodeHtml(code), true);

        mailSender.send(message);
    }

    /** 인증코드 검증 */
    public boolean verifyCode(String email, String inputCode) {
        long[] stored = codeStore.get(email);
        if (stored == null) return false;

        if (System.currentTimeMillis() > stored[1]) {
            codeStore.remove(email);
            verifiedStore.remove(email);
            return false;
        }

        boolean match = String.valueOf(stored[0]).equals(inputCode == null ? "" : inputCode.trim());
        if (match) {
            codeStore.remove(email);
            verifiedStore.put(email, System.currentTimeMillis() + VERIFIED_EXPIRY_MS);
        }
        return match;
    }

    /** 현재 인증 완료 상태인지 확인 */
    public boolean isVerified(String email) {
        Long expiry = verifiedStore.get(email);
        if (expiry == null) return false;

        if (System.currentTimeMillis() > expiry) {
            verifiedStore.remove(email);
            return false;
        }
        return true;
    }

    /** 인증 완료 상태를 1회성으로 소비 */
    public boolean consumeVerified(String email) {
        if (!isVerified(email)) return false;
        verifiedStore.remove(email);
        return true;
    }

    private String buildCodeHtml(String code) {
        return "<!DOCTYPE html><html lang='ko'><head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:0;background:#f4f6fb;font-family:Apple SD Gothic Neo,sans-serif;'>"
                + "<div style='max-width:480px;margin:40px auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>"
                + "<div style='background:linear-gradient(135deg,#1e3a5f,#2a5298);padding:32px;text-align:center;'>"
                + "<div style='font-size:24px;font-weight:800;color:#fff;letter-spacing:6px;'>JOBMATE</div></div>"
                + "<div style='padding:36px 32px;text-align:center;'>"
                + "<p style='font-size:16px;font-weight:600;color:#1e3a5f;margin:0 0 8px;'>이메일 인증 코드</p>"
                + "<p style='font-size:13px;color:#64748b;margin:0 0 28px;'>아래 6자리 코드를 입력해주세요. 코드는 5분간 유효합니다.</p>"
                + "<div style='display:inline-block;background:#f0f4f8;border:2px solid #dce8f0;border-radius:12px;padding:16px 40px;margin-bottom:24px;'>"
                + "<span style='font-size:36px;font-weight:900;color:#1e3a5f;letter-spacing:8px;'>" + code + "</span></div>"
                + "<p style='font-size:12px;color:#94a3b8;margin:0;'>본 메일은 JobMate에서 자동 발송되었습니다.</p></div></div>"
                + "</body></html>";
    }
}