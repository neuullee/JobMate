package com.ama.jobmate;

import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.repository.JobPostingRepository;
import com.ama.jobmate.service.CsvImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

//    CsvImportService 테스트 개요
//
//    정상 CSV 적재
//    DB 중복 jobId skip
//    컬럼 부족 row skip
//    일부 row 실패 후 다음 row 계속 저장

    @Mock
    private JobPostingRepository jobPostingRepository;

    @InjectMocks
    private CsvImportService csvImportService;

    private String header;

    @BeforeEach
    void setUp() {
        header = "JobId,Rank,Score,Title,Company,URL,Deadline,TechStack,JobLevel,IsRemote,AI_Summary,AI_Reason,StackScore,LevelScore,RemoteScore,FullDescription,Location\n";
    }

    @Test
    void importFromCsv_shouldSaveValidRows() throws Exception {
        String body = header + validRow("JOB-1", "https://example.com/1") + "\n";
        MockMultipartFile file = csvFile(body);

        when(jobPostingRepository.findByJobId("JOB-1")).thenReturn(Optional.empty());
        when(jobPostingRepository.existsByDuplicateGroupKey(anyString())).thenReturn(false);

        int savedCount = csvImportService.importFromCsv(file);

        assertEquals(1, savedCount);

        ArgumentCaptor<List<JobPosting>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobPostingRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());

        JobPosting saved = captor.getValue().get(0);
        assertEquals("JOB-1", saved.getJobId());
        assertEquals("백엔드 개발자", saved.getTitle());
        assertEquals("좋은회사", saved.getCompany());
        assertEquals("SEOUL", saved.getNormalizedLocation());
        assertEquals("BACKEND", saved.getNormalizedJob());
        assertTrue(Boolean.TRUE.equals(saved.getIsValid()));
        assertFalse(Boolean.TRUE.equals(saved.getIsDuplicate()));
    }

    @Test
    void importFromCsv_shouldSkipDuplicateJobIdAlreadyInDatabase() throws Exception {
        String body = header + validRow("JOB-1", "https://example.com/1") + "\n";
        MockMultipartFile file = csvFile(body);

        when(jobPostingRepository.findByJobId("JOB-1")).thenReturn(Optional.of(new JobPosting()));

        int savedCount = csvImportService.importFromCsv(file);

        assertEquals(0, savedCount);
        verify(jobPostingRepository, never()).saveAll(any());
    }

    @Test
    void importFromCsv_shouldSkipRowWhenColumnsAreInsufficient() throws Exception {
        String invalidRow = "JOB-1,1,4800,백엔드 개발자,좋은회사";
        MockMultipartFile file = csvFile(header + invalidRow + "\n");

        int savedCount = csvImportService.importFromCsv(file);

        assertEquals(0, savedCount);
        verify(jobPostingRepository, never()).saveAll(any());
    }

    @Test
    void importFromCsv_shouldContinueWhenOneRowFailsAndSaveNextRow() throws Exception {
        String firstRow = validRow("", "https://example.com/invalid");
        String secondRow = validRow("JOB-2", "https://example.com/2");
        MockMultipartFile file = csvFile(header + firstRow + "\n" + secondRow + "\n");

        when(jobPostingRepository.findByJobId("")).thenReturn(Optional.empty());
        when(jobPostingRepository.findByJobId("JOB-2")).thenReturn(Optional.empty());
        when(jobPostingRepository.existsByDuplicateGroupKey(anyString())).thenReturn(false);

        int savedCount = csvImportService.importFromCsv(file);

        assertEquals(1, savedCount);
        verify(jobPostingRepository).saveAll(any());
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file",
                "jobs.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String validRow(String jobId, String url) {
        return String.join(",",
                wrap(jobId),
                wrap("1"),
                wrap("4800"),
                wrap("백엔드 개발자"),
                wrap("좋은회사"),
                wrap(url),
                wrap("채용중"),
                wrap("Java Spring MySQL"),
                wrap("정규직"),
                wrap("N"),
                wrap("백엔드 서비스 개발"),
                wrap("Java 기반 서비스 운영"),
                wrap("95"),
                wrap("90"),
                wrap("80"),
                wrap("Java Spring 기반 백엔드 개발 업무입니다. API 설계와 MySQL 운영 경험이 필요합니다."),
                wrap("서울")
        );
    }

    private String wrap(String value) {
        return '"' + value + '"';
    }
}
