package com.ama.jobmate.controller;

import com.ama.jobmate.dto.ApplicationDto;
import com.ama.jobmate.service.ApplicationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ApplicationDto>> getApplications(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).build();
        List<ApplicationDto> list = applicationService.getApplications(memberId)
                .stream().map(ApplicationDto::new).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<ApplicationDto> addApplication(
            @RequestBody Map<String, String> req, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(new ApplicationDto(applicationService.addApplication(memberId, req)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationDto> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, String> req) {
        return ResponseEntity.ok(new ApplicationDto(applicationService.updateStatus(id, req.get("status"))));
    }

    @PatchMapping("/{id}/memo")
    public ResponseEntity<ApplicationDto> updateMemo(
            @PathVariable Long id, @RequestBody Map<String, String> req) {
        return ResponseEntity.ok(new ApplicationDto(applicationService.updateMemo(id, req.get("memo"))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        applicationService.deleteApplication(id);
        return ResponseEntity.ok().build();
    }
}