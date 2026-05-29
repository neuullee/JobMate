package com.ama.jobmate.service;

import com.ama.jobmate.entity.Application;
import com.ama.jobmate.entity.ApplicationStatus;
import com.ama.jobmate.entity.Member;
import com.ama.jobmate.repository.ApplicationRepository;
import com.ama.jobmate.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;

    public List<Application> getApplications(Long memberId) {
        return applicationRepository.findByMemberIdOrderByAppliedDateDesc(memberId);
    }

    public Application addApplication(Long memberId, Map<String, String> req) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("회원 없음"));

        Application app = new Application();
        app.setMember(member);
        app.setCompanyName(req.get("companyName"));
        app.setJobTitle(req.get("jobTitle"));

        // 지원일
        String appliedDate = req.get("appliedDate");
        app.setAppliedDate(appliedDate != null && !appliedDate.isEmpty()
                ? LocalDate.parse(appliedDate)
                : LocalDate.now());

        // ✅ 마감일
        String deadline = req.get("deadline");
        if (deadline != null && !deadline.isEmpty()) {
            app.setDeadline(LocalDate.parse(deadline));
        }

        app.setMemo(req.get("memo"));
        return applicationRepository.save(app);
    }

    public Application updateStatus(Long applicationId, String status) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("지원 내역 없음"));
        app.setStatus(ApplicationStatus.valueOf(status));
        return applicationRepository.save(app);
    }

    public Application updateMemo(Long applicationId, String memo) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("지원 내역 없음"));
        app.setMemo(memo);
        return applicationRepository.save(app);
    }

    public void deleteApplication(Long applicationId) {
        applicationRepository.deleteById(applicationId);
    }
}
