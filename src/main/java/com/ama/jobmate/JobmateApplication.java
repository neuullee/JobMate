package com.ama.jobmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // 비동기 백그라운드 실행 활성화
public class JobmateApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobmateApplication.class, args);
    }
}
