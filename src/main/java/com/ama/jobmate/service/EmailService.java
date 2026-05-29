package com.ama.jobmate.service;

import com.ama.jobmate.dto.JobMatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * URL이 살아있는지 확인 (마감된 공고 제외용)
     * - HTTP 200 응답 + 사람인 마감 키워드 없으면 유효
     * - 연결 실패 또는 404면 무효
     * - Accept-Encoding: identity 로 gzip 비활성화 (압축 시 한글 키워드 매칭 실패 방지)
     */
    private boolean isUrlAlive(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) return false;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9");
            // gzip 비활성화 → 압축된 바이트를 그냥 읽으면 한글 키워드 매칭 실패
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();

            // 404, 410 Gone → 확실히 마감
            if (status == 404 || status == 410) {
                conn.disconnect();
                return false;
            }

            // 200이면 응답 본문에서 마감 키워드 확인
            if (status == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int read;
                int totalRead = 0;
                while ((read = is.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                    totalRead += read;
                    if (totalRead >= 65536) break; // 최대 64KB
                }
                conn.disconnect();

                String body = baos.toString("UTF-8");

                if (body.contains("본 채용정보는 마감") ||
                    body.contains("마감되었습니다") ||
                    body.contains("채용이 마감") ||
                    body.contains("마감된 공고") ||
                    body.contains("채용정보를 찾을 수 없") ||
                    body.contains("존재하지 않는 공고") ||
                    body.contains("이미 마감") ||
                    body.contains("접수가 마감") ||
                    body.contains("공고가 삭제") ||
                    body.contains("채용이 종료") ||
                    body.contains("종료된 공고")) {
                    return false;
                }
                return true;
            }

            conn.disconnect();
            // 그 외 상태코드 (3xx 리다이렉트 등) → 일단 유효로 처리
            return status < 400;

        } catch (Exception e) {
            // 연결 자체 실패 → 마감 or 삭제된 것으로 간주
            return false;
        }
    }

    /**
     * 살아있는 공고만 필터링해서 메일 발송
     * - 최대 5개가 될 때까지 순서대로 유효한 공고 선별
     * - URL 없는 공고는 제외
     */
    public void sendTopJobsMail(String toEmail, String memberName, List<JobMatchResult> jobs) throws Exception {
        // 유효한 공고만 필터링 (최대 5개)
        // 상위 5개만 추출 (deadline 필터는 JobMatchService에서 처리됨)
        List<JobMatchResult> aliveJobs = jobs.stream()
                .limit(5)
                .collect(java.util.stream.Collectors.toList());

        if (aliveJobs.isEmpty()) {
            throw new IllegalStateException("현재 유효한 공고가 없습니다. 잠시 후 다시 시도해주세요.");
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("[JobMate] " + memberName + "님의 맞춤 채용공고 TOP " + aliveJobs.size());
        helper.setText(buildHtml(memberName, aliveJobs), true);

        mailSender.send(message);
    }

    private String buildHtml(String memberName, List<JobMatchResult> jobs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='ko'><head><meta charset='UTF-8'></head>")
          .append("<body style='margin:0;padding:0;background:#f4f6fb;font-family:Apple SD Gothic Neo,sans-serif;'>");

        sb.append("<div style='max-width:620px;margin:40px auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>");

        // 헤더
        sb.append("<div style='background:linear-gradient(135deg,#6c47ff,#a78bfa);padding:36px 32px;text-align:center;'>")
          .append("<div style='font-size:28px;font-weight:800;color:#fff;letter-spacing:-0.5px;'>JOBMATE</div>")
          .append("<div style='color:rgba(255,255,255,0.85);font-size:14px;margin-top:8px;'>맞춤 채용공고 추천</div>")
          .append("</div>");

        // 인사말
        sb.append("<div style='padding:32px 32px 0;'>")
          .append("<p style='font-size:17px;font-weight:600;color:#1e1b4b;margin:0 0 6px;'>")
          .append("안녕하세요, <span style='color:#6c47ff;'>").append(escapeHtml(memberName)).append("</span>님 👋</p>")
          .append("<p style='font-size:14px;color:#64748b;margin:0 0 28px;'>")
          .append("회원님의 프로필을 분석해 현재 진행 중인 공고 <strong>TOP ").append(jobs.size()).append("</strong>을 선별했습니다.</p>")
          .append("</div>");

        // 공고 카드
        sb.append("<div style='padding:0 32px 32px;'>");
        for (int i = 0; i < jobs.size(); i++) {
            JobMatchResult result = jobs.get(i);
            String title     = result.getJob() != null ? result.getJob().getTitle()     : "";
            String company   = result.getJob() != null ? result.getJob().getCompany()   : "";
            String location  = result.getJob() != null ? result.getJob().getLocation()  : "";
            String salary    = result.getJob() != null ? result.getJob().getSalary()    : "";
            String techStack = result.getJob() != null ? result.getJob().getTechStack() : "";
            String url       = result.getJob() != null ? result.getJob().getUrl()       : "";

            String rankColor = i == 0 ? "#6c47ff" : i == 1 ? "#8b5cf6" : "#a78bfa";

            sb.append("<div style='border:1.5px solid #e8e4ff;border-radius:12px;padding:20px;margin-bottom:14px;background:#fafafe;'>")
              .append("<div style='display:flex;align-items:flex-start;gap:12px;'>")
              .append("<div style='min-width:32px;height:32px;background:").append(rankColor)
              .append(";border-radius:50%;color:#fff;font-weight:700;font-size:14px;")
              .append("display:flex;align-items:center;justify-content:center;flex-shrink:0;'>").append(i + 1).append("</div>")
              .append("<div style='flex:1;'>")
              .append("<div style='font-size:15px;font-weight:700;color:#1e1b4b;margin-bottom:4px;'>").append(escapeHtml(title)).append("</div>")
              .append("<div style='font-size:13px;color:#6c47ff;font-weight:600;margin-bottom:10px;'>").append(escapeHtml(company)).append("</div>")
              .append("<div style='display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px;'>");

            if (location != null && !location.isBlank())
                sb.append(tag("📍 " + location, "#f1f5f9", "#475569"));
            if (salary != null && !salary.isBlank())
                sb.append(tag("💰 " + salary, "#f0fdf4", "#166534"));
            if (result.getScore() > 0)
                sb.append(tag("매칭 " + result.getScore() + "점", "#ede9fe", "#5b21b6"));

            sb.append("</div>");

            if (techStack != null && !techStack.isBlank())
                sb.append("<div style='font-size:12px;color:#94a3b8;margin-bottom:12px;'>🛠 ").append(escapeHtml(techStack)).append("</div>");

            if (result.getAiSummary() != null && !result.getAiSummary().isBlank())
                sb.append("<div style='font-size:13px;color:#475569;background:#f8f7ff;border-left:3px solid #a78bfa;")
                  .append("padding:10px 12px;border-radius:6px;margin-bottom:12px;'>")
                  .append(escapeHtml(result.getAiSummary())).append("</div>");

            if (url != null && !url.isBlank())
                sb.append("<a href='").append(url)
                  .append("' style='display:inline-block;background:#6c47ff;color:#fff;font-size:13px;font-weight:600;")
                  .append("padding:8px 18px;border-radius:8px;text-decoration:none;'>공고 보기 →</a>");

            sb.append("</div></div></div>");
        }
        sb.append("</div>");

        // 푸터
        sb.append("<div style='background:#f8f7ff;padding:20px 32px;text-align:center;border-top:1px solid #ede9fe;'>")
          .append("<p style='font-size:12px;color:#94a3b8;margin:0;'>본 메일은 JobMate에서 자동 발송되었습니다.</p>")
          .append("<p style='font-size:12px;color:#94a3b8;margin:4px 0 0;'>© 2026 JobMate. All rights reserved.</p>")
          .append("</div></div></body></html>");

        return sb.toString();
    }

    private String tag(String text, String bg, String color) {
        return "<span style='background:" + bg + ";color:" + color
                + ";font-size:12px;font-weight:500;padding:3px 9px;border-radius:20px;'>"
                + escapeHtml(text) + "</span>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
