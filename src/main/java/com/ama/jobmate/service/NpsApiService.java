package com.ama.jobmate.service;

import com.ama.jobmate.entity.CompanyStat;
import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.repository.CompanyStatRepository;
import com.ama.jobmate.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NpsApiService {

    @Value("${nps.api-key}")
    private String apiKey;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    private final CompanyStatRepository companyStatRepository;
    private final DartApiService dartApiService;
    private final JobPostingRepository jobPostingRepository;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://apis.data.go.kr")
            .build();

    private final WebClient kakaoWebClient = WebClient.builder()
            .baseUrl("https://dapi.kakao.com")
            .build();

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

    private Map callNpsApi(String searchName, int numOfRows) {
        try {
            final String sn = searchName;
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/B552015/NpsBplcInfoInqireServiceV2/getBassInfoSearchV2")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("pageNo", "1")
                            .queryParam("numOfRows", String.valueOf(numOfRows))
                            .queryParam("dataType", "json")
                            .queryParam("wkplNm", sn)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            System.out.println("NPS API 호출 오류: " + e.getMessage());
            return null;
        }
    }

    private List extractItems(Map response) {
        if (response == null) return null;
        Map body = null;
        if (response.containsKey("body")) {
            body = (Map) response.get("body");
        } else if (response.containsKey("response")) {
            Map inner = (Map) response.get("response");
            if (inner != null) body = (Map) inner.get("body");
        } else if (response.containsKey("items")) {
            body = response;
        }
        if (body == null) return null;

        Object totalCount = body.get("totalCount");
        if (totalCount != null) {
            System.out.println("NPS totalCount: " + totalCount);
        }

        Map items = (Map) body.get("items");
        if (items == null) return null;
        Object itemObj = items.get("item");
        if (itemObj == null) return null;
        if (itemObj instanceof List) return (List) itemObj;
        List single = new java.util.ArrayList();
        single.add(itemObj);
        return single;
    }

    private Map findBestItem(List items, String originalName, String cleanName) {
        if (items == null || items.isEmpty()) return null;

        List<Map> exactMatches = new java.util.ArrayList<>();
        for (Object obj : items) {
            Map item = (Map) obj;
            String npsName = getString(item, "wkplNm");
            if (npsName == null) continue;
            String npsClean = normalizeCompanyName(npsName);
            if (npsName.equals(originalName) || npsName.equals(cleanName)
                    || npsClean.equals(cleanName)) {
                exactMatches.add(item);
            }
        }

        if (!exactMatches.isEmpty()) {
            Map best = pickByMaxJnCnt(exactMatches);
            System.out.println("NPS 정확매칭 선택: " + getString(best, "wkplNm")
                    + " jnCnt=" + getString(best, "jnCnt")
                    + " 상태=" + getString(best, "wkplJngStcd")
                    + " 주소=" + getString(best, "wkplRoadNmDtlAddr"));
            System.out.println("NPS RAW 키목록: " + best.keySet());
            return best;
        }

        List<Map> containsMatches = new java.util.ArrayList<>();
        for (Object obj : items) {
            Map item = (Map) obj;
            String npsClean = normalizeCompanyName(getString(item, "wkplNm"));
            if (npsClean != null && (npsClean.contains(cleanName) || cleanName.contains(npsClean))) {
                containsMatches.add(item);
            }
        }

        if (!containsMatches.isEmpty()) {
            Map best = pickByMaxJnCnt(containsMatches);
            System.out.println("NPS 포함매칭 선택: " + getString(best, "wkplNm")
                    + " jnCnt=" + getString(best, "jnCnt")
                    + " 상태=" + getString(best, "wkplJngStcd"));
            System.out.println("NPS RAW 키목록: " + best.keySet());
            return best;
        }

        // fallback: 정확/포함 매칭 모두 실패 → null 반환 (다른 회사 데이터 오염 방지)
        System.out.println("NPS 매칭 실패 (정확/포함 모두 없음): " + originalName + " → null 반환");
        return null;
    }

    private Map pickByMaxJnCnt(List items) {
        Map bestByJn = null;
        double maxJn = -1;
        Map bestActive = null;

        for (Object obj : items) {
            Map item = (Map) obj;
            String jnStr = getString(item, "jnCnt");
            if (jnStr != null) {
                try {
                    double jn = Double.parseDouble(jnStr.replace(",", "").trim());
                    if (jn > maxJn) { maxJn = jn; bestByJn = item; }
                } catch (Exception ignored) {}
            }
            if ("1".equals(getString(item, "wkplJngStcd")) && bestActive == null) {
                bestActive = item;
            }
        }

        if (bestByJn != null) return bestByJn;
        if (bestActive != null) return bestActive;
        return (Map) items.get(0);
    }

    public CompanyStat fetchAndSave(String companyName) {
        try {
            String cleanName = normalizeCompanyName(companyName);
            System.out.println("NPS 검색 시작: 원본=" + companyName + " / 정규화=" + cleanName);

            List items = extractItems(callNpsApi(companyName, 10));

            if ((items == null || items.isEmpty()) && !cleanName.equals(companyName)) {
                System.out.println("NPS 정규화명 재검색: " + cleanName);
                items = extractItems(callNpsApi(cleanName, 10));
            }

            if ((items == null || items.isEmpty()) && cleanName.length() >= 4) {
                String shortName = cleanName.substring(0, 4);
                System.out.println("NPS 단축명 재검색: " + shortName);
                items = extractItems(callNpsApi(shortName, 20));
            }

            CompanyStat stat = companyStatRepository
                    .findByCompanyName(companyName)
                    .orElse(new CompanyStat());
            stat.setCompanyName(companyName);

            Map item = findBestItem(items, companyName, cleanName);
            boolean npsMatched = false;
            if (item != null) {
                // NPS 매칭 성공: 정확히 이 회사 데이터임이 확인된 경우만 저장
                stat.setSeq(getString(item, "seq"));
                stat.setBzowrRgstsNo(getString(item, "bzowrRgstsNo"));
                stat.setDataCrtYm(getString(item, "dataCrtYm"));
                stat.setWkplStylDvcd(getString(item, "wkplStylDvcd"));
                stat.setWkplJngStcd(getString(item, "wkplJngStcd"));
                stat.setWkplRoadNmDtlAddr(getString(item, "wkplRoadNmDtlAddr"));
                stat.setLdongAddrMgplDgCd(getString(item, "ldongAddrMgplDgCd"));
                stat.setLdongAddrMgplSgguCd(getString(item, "ldongAddrMgplSgguCd"));
                stat.setLdongAddrMgplSgguEmdCd(getString(item, "ldongAddrMgplSgguEmdCd"));
                calculateLeaveRate(stat, item);
                npsMatched = true;
            } else {
                System.out.println("NPS 매칭 실패 (데이터 없음): " + companyName);
            }

            dartApiService.enrichWithDartData(stat, companyName);

            if (stat.getLeaveRate() == null && npsMatched) {
                calculateLeaveRateFromDart(stat);
            }

            // NPS도 DART도 아무 데이터도 없으면 DB에 저장하지 않음 (빈 캐시 방지)
            boolean hasSomeData = npsMatched
                    || stat.getDartCorpCode() != null
                    || stat.getBzowrRgstsNo() != null;
            if (!hasSomeData) {
                System.out.println("NPS+DART 모두 데이터 없음, DB 저장 생략: " + companyName);
                return null;
            }

            return companyStatRepository.save(stat);

        } catch (Exception e) {
            System.out.println("NPS API 오류: " + e.getMessage());
            return null;
        }
    }

    private void calculateLeaveRate(CompanyStat stat, Map item) {
        try {
            String whdwCntStr = getString(item, "whdwCnt");
            String jnCntStr   = getString(item, "jnCnt");

            if (whdwCntStr != null && jnCntStr != null) {
                double whdwCnt = Double.parseDouble(whdwCntStr.replace(",", "").trim());
                double jnCnt   = Double.parseDouble(jnCntStr.replace(",", "").trim());
                if (jnCnt > 0) {
                    double rate = (whdwCnt / jnCnt) * 100.0;
                    stat.setLeaveRate(Math.round(rate * 10.0) / 10.0);
                    System.out.println("leaveRate(NPS 탈퇴/가입): " + stat.getLeaveRate() + "%");
                    return;
                }
            }

            String mxmJnCntStr = getString(item, "mxmJnCnt");
            if (jnCntStr != null && mxmJnCntStr != null) {
                double jnCnt    = Double.parseDouble(jnCntStr.replace(",", "").trim());
                double mxmJnCnt = Double.parseDouble(mxmJnCntStr.replace(",", "").trim());
                if (mxmJnCnt > 0 && mxmJnCnt > jnCnt) {
                    double rate = ((mxmJnCnt - jnCnt) / mxmJnCnt) * 100.0;
                    stat.setLeaveRate(Math.round(rate * 10.0) / 10.0);
                    System.out.println("leaveRate(NPS 최대-현재): " + stat.getLeaveRate() + "%");
                }
            }
        } catch (Exception e) {
            System.out.println("leaveRate NPS 계산 오류: " + e.getMessage());
        }
    }

    private void calculateLeaveRateFromDart(CompanyStat stat) {
        if (stat.getRevenue() == null || stat.getRevenue() == 0) return;
        if (stat.getOperatingProfit() == null) return;

        double margin = (stat.getOperatingProfit().doubleValue() / stat.getRevenue().doubleValue()) * 100.0;
        System.out.println("DART 영업이익률: " + String.format("%.1f", margin) + "% → leaveRate 추정");

        if (margin > 10) {
            stat.setLeaveRate(5.0);
        } else if (margin > 0) {
            stat.setLeaveRate(15.0);
        } else {
            stat.setLeaveRate(25.0);
        }
        System.out.println("leaveRate(DART 추정): " + stat.getLeaveRate() + "%");
    }

    /**
     * [BUG FIX] 캐시 조건 수정
     * 기존: wkplJngStcd != null || dartCorpCode != null 일 때만 캐시 사용
     * → DART 매칭 실패 시 무한 재조회 문제 발생
     * 수정: DB에 있으면 무조건 캐시 사용 (재조회 방지)
     *       단, 데이터가 전혀 없는 빈 레코드면 재조회
     */
    public CompanyStat getCompanyStat(String companyName) {
        return companyStatRepository.findByCompanyName(companyName)
                .filter(s -> s.getId() != null)  // DB에 저장된 레코드면 그대로 사용
                .orElseGet(() -> fetchAndSave(companyName));
    }

    public double[] geocodeAddress(String address) {
        if (address == null || address.isBlank()) return null;
        try {
            final String addr = address;
            Map response = kakaoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/address.json")
                            .queryParam("query", addr)
                            .queryParam("size", "1")
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return null;
            List documents = (List) response.get("documents");
            if (documents == null || documents.isEmpty()) return geocodeKeyword(address);

            Map doc = (Map) documents.get(0);
            double lat = Double.parseDouble((String) doc.get("y"));
            double lng = Double.parseDouble((String) doc.get("x"));
            return new double[]{lat, lng};

        } catch (Exception e) {
            System.out.println("지오코딩 오류: " + e.getMessage());
            return null;
        }
    }

    private double[] geocodeKeyword(String keyword) {
        try {
            final String kw = keyword;
            Map response = kakaoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", kw)
                            .queryParam("size", "1")
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return null;
            List documents = (List) response.get("documents");
            if (documents == null || documents.isEmpty()) return null;

            Map doc = (Map) documents.get(0);
            return new double[]{
                Double.parseDouble((String) doc.get("y")),
                Double.parseDouble((String) doc.get("x"))
            };
        } catch (Exception e) {
            return null;
        }
    }

    public void fillJobCoordinates(JobPosting job) {
        if (job.getLat() != null && job.getLng() != null) return;
        CompanyStat stat = companyStatRepository.findByCompanyName(job.getCompany()).orElse(null);
        String address = null;
        if (stat != null && stat.getWkplRoadNmDtlAddr() != null) {
            address = stat.getWkplRoadNmDtlAddr();
        } else if (job.getLocation() != null) {
            address = job.getLocation();
        }
        if (address == null) return;
        double[] coords = geocodeAddress(address);
        if (coords != null) {
            job.setLat(coords[0]);
            job.setLng(coords[1]);
            jobPostingRepository.save(job);
        }
    }

    private String getString(Map map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
