package com.ama.jobmate.controller;

import com.ama.jobmate.repository.MemberProfileRepository;
import com.ama.jobmate.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final MemberProfileRepository memberProfileRepository;

    @PostMapping("/import-csv")
    public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "파일이 비어있습니다."
            ));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "CSV 파일만 업로드 가능합니다."
            ));
        }

        try {
            int savedCount = csvImportService.importFromCsv(file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", savedCount + "건의 채용공고가 저장되었습니다.",
                    "savedCount", savedCount
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "임포트 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/search-keywords")
    public ResponseEntity<?> getSearchKeywords() {
        try {
            List<String> keywords = memberProfileRepository.findDistinctDesiredJobs();

            if (keywords == null || keywords.isEmpty()) {
                keywords = List.of("개발자");
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "keywords", keywords,
                    "count", keywords.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "키워드 조회 실패: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/job-exists")
    public ResponseEntity<?> checkJobExists(@RequestParam("jobId") String jobId) {
        try {
            long count = csvImportService.countAllJobs();
            System.out.println("=== DB COUNT = " + count);

            boolean exists = csvImportService.existsByJobId(jobId);
            System.out.println("=== CHECK jobId = " + jobId + " / exists = " + exists);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "jobId", jobId,
                    "exists", exists
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "jobId 조회 실패: " + e.getMessage()
            ));
        }
    }
    @GetMapping("/job-ids")
    public ResponseEntity<?> getAllJobIds() {
        try {
            List<String> jobIds = csvImportService.getAllJobIds();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", jobIds.size(),
                    "jobIds", jobIds
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "jobId 목록 조회 실패: " + e.getMessage()
            ));
        }
    }
}