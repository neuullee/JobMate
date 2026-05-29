package com.ama.jobmate.service;

import com.ama.jobmate.entity.CompanyStat;
import com.ama.jobmate.repository.CompanyStatRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class DartApiService {

    @Value("${dart.api-key}")
    private String apiKey;

    private final CompanyStatRepository companyStatRepository;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
            .build();

    private Map<String, String> corpCodeMap = new HashMap<>();

    // ── [BUG FIX] 영문/약칭 → DART 등록명 별칭 매핑 ──
    private static final Map<String, String> COMPANY_ALIAS = new HashMap<>();
    static {
        COMPANY_ALIAS.put("NC소프트",   "엔씨소프트");
        COMPANY_ALIAS.put("NCsoft",     "엔씨소프트");
        COMPANY_ALIAS.put("NCSoft",     "엔씨소프트");
        COMPANY_ALIAS.put("넥슨",       "넥슨코리아");
        COMPANY_ALIAS.put("Nexon",      "넥슨코리아");
        COMPANY_ALIAS.put("카카오",     "카카오");
        COMPANY_ALIAS.put("Kakao",      "카카오");
        COMPANY_ALIAS.put("네이버",     "NAVER");
        COMPANY_ALIAS.put("Naver",      "NAVER");
        COMPANY_ALIAS.put("라인",       "라인플러스");
        COMPANY_ALIAS.put("LINE",       "라인플러스");
        COMPANY_ALIAS.put("쿠팡",       "쿠팡");
        COMPANY_ALIAS.put("Coupang",    "쿠팡");
        COMPANY_ALIAS.put("배민",       "우아한형제들");
        COMPANY_ALIAS.put("배달의민족", "우아한형제들");
        COMPANY_ALIAS.put("토스",       "비바리퍼블리카");
        COMPANY_ALIAS.put("Toss",       "비바리퍼블리카");
        COMPANY_ALIAS.put("당근",       "당근마켓");
        COMPANY_ALIAS.put("당근마켓",   "당근마켓");
        COMPANY_ALIAS.put("크래프톤",   "크래프톤");
        COMPANY_ALIAS.put("KRAFTON",    "크래프톤");
        COMPANY_ALIAS.put("컴투스",     "컴투스");
        COMPANY_ALIAS.put("Com2uS",     "컴투스");
        COMPANY_ALIAS.put("펄어비스",   "펄어비스");
        COMPANY_ALIAS.put("Pearl Abyss","펄어비스");
        COMPANY_ALIAS.put("스마일게이트","스마일게이트홀딩스");
        COMPANY_ALIAS.put("Smilegate",  "스마일게이트홀딩스");
        COMPANY_ALIAS.put("위메이드",   "위메이드");
        COMPANY_ALIAS.put("Wemade",     "위메이드");
    }

    @PostConstruct
    public void loadCorpCodes() {
        try {
            System.out.println("DART corpCode.xml 로딩 시작...");
            byte[] zipBytes = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/corpCode.xml")
                            .queryParam("crtfc_key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (zipBytes == null) { System.out.println("DART corpCode.xml 다운로드 실패"); return; }

            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
            zis.getNextEntry();
            byte[] xmlBytes = zis.readAllBytes();
            zis.close();

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xmlBytes));

            NodeList list = doc.getElementsByTagName("list");
            for (int i = 0; i < list.getLength(); i++) {
                Element el = (Element) list.item(i);
                String corpCode = el.getElementsByTagName("corp_code").item(0).getTextContent();
                String corpName = el.getElementsByTagName("corp_name").item(0).getTextContent();
                corpCodeMap.put(corpName, corpCode);
            }
            System.out.println("DART corpCode 로딩 완료: " + corpCodeMap.size() + "개 기업");

        } catch (Exception e) {
            System.out.println("DART corpCode 로딩 오류: " + e.getMessage());
        }
    }

    private String normalizeCompanyName(String name) {
        if (name == null) return "";
        return name
                .replace("(주)", "").replace("주식회사", "")
                .replace("(유)", "").replace("유한회사", "")
                .replace("(합)", "").replace("협동조합", "")
                .replace("㈜", "").replace("(사)", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("[\\s]+", " ")
                .trim();
    }

    public String getCorpCode(String companyName) {
        if (corpCodeMap.isEmpty()) {
            System.out.println("DART corpCodeMap 비어있음 - 로딩 재시도");
            loadCorpCodes();
        }

        String cleanName = normalizeCompanyName(companyName);

        // 0. 별칭 매핑 먼저 확인
        String aliasName = COMPANY_ALIAS.get(companyName.trim());
        if (aliasName == null) aliasName = COMPANY_ALIAS.get(cleanName);
        if (aliasName != null) {
            String aliasCode = corpCodeMap.get(aliasName);
            if (aliasCode != null) {
                System.out.println("DART 별칭 매칭: " + companyName + " → " + aliasName);
                return aliasCode;
            }
        }

        // 1. 원본 정확 매칭
        if (corpCodeMap.containsKey(companyName)) {
            System.out.println("DART 정확 매칭(원본): " + companyName);
            return corpCodeMap.get(companyName);
        }
        // 2. 정규화 정확 매칭
        if (corpCodeMap.containsKey(cleanName)) {
            System.out.println("DART 정확 매칭(정규화): " + cleanName);
            return corpCodeMap.get(cleanName);
        }
        // 3. DART 기업명에서 정규화 후 정확 매칭
        for (Map.Entry<String, String> entry : corpCodeMap.entrySet()) {
            String dartClean = normalizeCompanyName(entry.getKey());
            if (dartClean.equals(cleanName)) {
                System.out.println("DART 정규화 정확 매칭: " + companyName + " → " + entry.getKey());
                return entry.getValue();
            }
        }
        // 4. 포함 매칭 (cleanName이 DART 기업명에 포함)
        for (Map.Entry<String, String> entry : corpCodeMap.entrySet()) {
            String dartClean = normalizeCompanyName(entry.getKey());
            if (dartClean.contains(cleanName) && cleanName.length() >= 2) {
                System.out.println("DART 포함 매칭(DART⊃입력): " + companyName + " → " + entry.getKey());
                return entry.getValue();
            }
        }
        // 5. 역방향 포함 매칭 (DART 기업명이 cleanName에 포함)
        for (Map.Entry<String, String> entry : corpCodeMap.entrySet()) {
            String dartClean = normalizeCompanyName(entry.getKey());
            if (cleanName.contains(dartClean) && dartClean.length() >= 2) {
                System.out.println("DART 포함 매칭(입력⊃DART): " + companyName + " → " + entry.getKey());
                return entry.getValue();
            }
        }

        System.out.println("DART 고유번호 없음: " + companyName);
        return null;
    }

    public Map getCorpInfo(String corpCode) {
        try {
            Map response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/company.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("corp_code", corpCode)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return null;
            if (!"000".equals(response.get("status"))) return null;
            return response;

        } catch (Exception e) {
            System.out.println("DART 기업정보 조회 오류: " + e.getMessage());
            return null;
        }
    }

    // 최신 연도 자동 탐색 (기존 메서드 유지)
    public Map getFinancialInfo(String corpCode) {
        String[] reprtCodes = {"11011", "11012", "11013", "11014"};
        String[] fsDivs = {"OFS", "CFS"};
        int currentYear = java.time.LocalDate.now().getYear();
        String[] years = {
                String.valueOf(currentYear - 1),
                String.valueOf(currentYear - 2),
                String.valueOf(currentYear)
        };

        for (String year : years) {
            for (String reprtCode : reprtCodes) {
                for (String fsDiv : fsDivs) {
                    try {
                        final String fd = fsDiv;
                        Map response = webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/api/fnlttSinglAcntAll.json")
                                        .queryParam("crtfc_key", apiKey)
                                        .queryParam("corp_code", corpCode)
                                        .queryParam("bsns_year", year)
                                        .queryParam("reprt_code", reprtCode)
                                        .queryParam("fs_div", fd)
                                        .build())
                                .retrieve()
                                .bodyToMono(Map.class)
                                .block();

                        if (response == null) continue;
                        if (!"000".equals(response.get("status"))) continue;
                        List list = (List) response.get("list");
                        if (list == null || list.isEmpty()) continue;

                        System.out.println("DART 재무 조회 성공: year=" + year + " reprt=" + reprtCode + " fs=" + fsDiv);
                        return response;

                    } catch (Exception e) {
                        System.out.println("DART 재무정보 조회 오류: " + e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    // ── [신규] 특정 연도 재무정보 조회 (3개년 JSON 적재용) ──
    public Map getFinancialInfoByYear(String corpCode, String year) {
        String[] reprtCodes = {"11011", "11012", "11013", "11014"};
        String[] fsDivs = {"OFS", "CFS"};

        for (String reprtCode : reprtCodes) {
            for (String fsDiv : fsDivs) {
                try {
                    final String fd = fsDiv;
                    Map response = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/fnlttSinglAcntAll.json")
                                    .queryParam("crtfc_key", apiKey)
                                    .queryParam("corp_code", corpCode)
                                    .queryParam("bsns_year", year)
                                    .queryParam("reprt_code", reprtCode)
                                    .queryParam("fs_div", fd)
                                    .build())
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

                    if (response == null) continue;
                    if (!"000".equals(response.get("status"))) continue;
                    List list = (List) response.get("list");
                    if (list == null || list.isEmpty()) continue;

                    System.out.println("DART 연도별 재무 조회 성공: year=" + year + " reprt=" + reprtCode + " fs=" + fsDiv);
                    return response;

                } catch (Exception e) {
                    System.out.println("DART 연도별 재무정보 조회 오류: " + e.getMessage());
                }
            }
        }
        return null;
    }

    public Integer getEmployeeCount(String corpCode) {
        String[] reprtCodes = {"11011", "11012", "11013", "11014"};
        int currentYear = java.time.LocalDate.now().getYear();
        String[] years = {
                String.valueOf(currentYear - 1),
                String.valueOf(currentYear - 2),
                String.valueOf(currentYear)
        };

        for (String year : years) {
            for (String reprtCode : reprtCodes) {
                try {
                    Map response = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/empSttus.json")
                                    .queryParam("crtfc_key", apiKey)
                                    .queryParam("corp_code", corpCode)
                                    .queryParam("bsns_year", year)
                                    .queryParam("reprt_code", reprtCode)
                                    .build())
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

                    if (response == null) continue;
                    if (!"000".equals(response.get("status"))) continue;

                    List items = (List) response.get("list");
                    if (items == null || items.isEmpty()) continue;

                    int total = 0;
                    for (Object obj : items) {
                        Map emp = (Map) obj;
                        String empType = (String) emp.get("sexdstn");
                        String cntStr  = (String) emp.get("tot_emp_cnt");
                        if (cntStr == null) cntStr = (String) emp.get("rgllbr_co");
                        if (cntStr == null) continue;
                        try {
                            total += Integer.parseInt(cntStr.replace(",", "").trim());
                        } catch (Exception ignored) {}
                        if ("합계".equals(empType) || "전체".equals(empType)) {
                            System.out.println("DART 사원수(합계행): year=" + year + " → " + total);
                            return total;
                        }
                    }
                    if (total > 0) {
                        System.out.println("DART 사원수(합산): year=" + year + " → " + total);
                        return total;
                    }

                } catch (Exception e) {
                    System.out.println("DART 사원수 조회 오류: " + e.getMessage());
                }
            }
        }
        return null;
    }

    public String getDividendInfo(String corpCode) {
        String[] reprtCodes = {"11011", "11012", "11013", "11014"};
        int currentYear = java.time.LocalDate.now().getYear();
        String[] years = {
                String.valueOf(currentYear - 1),
                String.valueOf(currentYear - 2)
        };

        for (String year : years) {
            for (String reprtCode : reprtCodes) {
                try {
                    Map response = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/alotMatter.json")
                                    .queryParam("crtfc_key", apiKey)
                                    .queryParam("corp_code", corpCode)
                                    .queryParam("bsns_year", year)
                                    .queryParam("reprt_code", reprtCode)
                                    .build())
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

                    if (response == null) continue;
                    if (!"000".equals(response.get("status"))) continue;
                    List items = (List) response.get("list");
                    if (items == null || items.isEmpty()) continue;

                    Map div = (Map) items.get(0);
                    String divAmount = (String) div.get("thstrm");
                    if (divAmount != null && !divAmount.isBlank() && !"-".equals(divAmount.trim())) {
                        return divAmount.replace(",", "") + "원";
                    }

                } catch (Exception e) {
                    System.out.println("DART 배당정보 조회 오류: " + e.getMessage());
                }
            }
        }
        return null;
    }

    public Integer getAverageSalary(String corpCode) {
        String[] reprtCodes = {"11011", "11012", "11013", "11014"};
        int currentYear = java.time.LocalDate.now().getYear();
        String[] years = {
                String.valueOf(currentYear - 1),
                String.valueOf(currentYear - 2),
                String.valueOf(currentYear)
        };

        for (String year : years) {
            for (String reprtCode : reprtCodes) {
                try {
                    Map response = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/empSttus.json")
                                    .queryParam("crtfc_key", apiKey)
                                    .queryParam("corp_code", corpCode)
                                    .queryParam("bsns_year", year)
                                    .queryParam("reprt_code", reprtCode)
                                    .build())
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

                    if (response == null) continue;
                    if (!"000".equals(response.get("status"))) continue;

                    List items = (List) response.get("list");
                    if (items == null || items.isEmpty()) continue;

                    for (Object obj : items) {
                        Map emp = (Map) obj;
                        String sexdstn = (String) emp.get("sexdstn");
                        // 합계 행에서 평균 급여 추출
                        if ("합계".equals(sexdstn) || "전체".equals(sexdstn)) {
                            String salStr = (String) emp.get("avg_anl_salmn");
                            if (salStr != null && !salStr.isBlank() && !"-".equals(salStr.trim())) {
                                try {
                                    int salary = Integer.parseInt(salStr.replace(",", "").trim());
                                    System.out.println("DART 평균연봉(합계행): year=" + year + " → " + salary + "만원");
                                    return salary;
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    // 합계 행이 없으면 첫 번째 행에서 시도
                    Map firstEmp = (Map) items.get(0);
                    String salStr = (String) firstEmp.get("avg_anl_salmn");
                    if (salStr != null && !salStr.isBlank() && !"-".equals(salStr.trim())) {
                        try {
                            int salary = Integer.parseInt(salStr.replace(",", "").trim());
                            System.out.println("DART 평균연봉(첫행): year=" + year + " → " + salary + "만원");
                            return salary;
                        } catch (Exception ignored) {}
                    }

                } catch (Exception e) {
                    System.out.println("DART 평균연봉 조회 오류: " + e.getMessage());
                }
            }
        }
        return null;
    }

    public void enrichWithDartData(CompanyStat stat, String companyName) {
        String corpCode = getCorpCode(companyName);
        if (corpCode == null) return;

        stat.setDartCorpCode(corpCode);

        Map corpInfo = getCorpInfo(corpCode);
        if (corpInfo != null) {
            stat.setCorpName((String) corpInfo.get("corp_name"));
            stat.setStockCode((String) corpInfo.get("stock_code"));
            stat.setCeoName((String) corpInfo.get("ceo_nm"));
            stat.setCorpClass((String) corpInfo.get("corp_cls"));
            stat.setEstablishDate((String) corpInfo.get("est_dt"));
            stat.setClosingMonth((String) corpInfo.get("acc_mt"));
            stat.setPhoneNumber((String) corpInfo.get("phn_no"));
            stat.setAddress((String) corpInfo.get("adres"));
            stat.setHomepageUrl((String) corpInfo.get("hm_url"));
            // DART bizr_no → NPS 번호가 없을 때만 채우기
            String bizrNo = (String) corpInfo.get("bizr_no");
            if (bizrNo != null && !bizrNo.isBlank()
                    && (stat.getBzowrRgstsNo() == null || stat.getBzowrRgstsNo().isBlank())) {
                stat.setBzowrRgstsNo(bizrNo);
            }
        }

        Map financial = getFinancialInfo(corpCode);
        if (financial != null) {
            List list = (List) financial.get("list");
            if (list != null) {
                for (Object obj : list) {
                    Map item = (Map) obj;
                    String account = (String) item.get("account_nm");
                    if (account == null) continue;

                    String amount = (String) item.get("thstrm_amount");
                    if (amount == null || amount.isBlank() || "-".equals(amount.trim())) {
                        amount = (String) item.get("thstrm_add_amount");
                    }
                    if (amount == null || amount.isBlank() || "-".equals(amount.trim())) continue;

                    long value;
                    try {
                        value = Long.parseLong(amount.replace(",", "").trim());
                    } catch (Exception e) {
                        continue;
                    }

                    String acLower = account.toLowerCase();
                    if (stat.getRevenue() == null &&
                        (account.equals("매출") || acLower.contains("매출액")
                         || acLower.contains("수익(매출") || acLower.contains("영업수익")
                         || acLower.contains("매출총액"))) {
                        stat.setRevenue(value);
                    } else if (stat.getOperatingProfit() == null &&
                               (acLower.contains("영업이익") || acLower.contains("영업손익"))) {
                        stat.setOperatingProfit(value);
                    } else if (stat.getNetIncome() == null &&
                               (account.equals("당기순이익") || acLower.contains("당기순이익")
                                || acLower.contains("당기순손익"))) {
                        stat.setNetIncome(value);
                    }
                }
            }
        }

        Integer empCount = getEmployeeCount(corpCode);
        if (empCount != null) stat.setEmployeeCount(empCount);

        String dividend = getDividendInfo(corpCode);
        if (dividend != null) stat.setDividendInfo(dividend);

        // ── 평균 연봉 ──
        Integer avgSalary = getAverageSalary(corpCode);
        if (avgSalary != null) stat.setAvgSalary(avgSalary);
    }
}
