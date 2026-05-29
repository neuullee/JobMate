package com.ama.jobmate.common.config;

import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final JobPostingRepository jobPostingRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (true) return; // 더미데이터 비활성화
    }
}