package com.ama.jobmate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "dart_corp_code")
public class DartCorpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "corp_code", unique = true)
    private String corpCode;       // DART 고유번호

    @Column(name = "corp_name")
    private String corpName;       // 회사명

    @Column(name = "stock_code")
    private String stockCode;      // 주식코드

    @Column(name = "bizr_no")
    private String bizrNo;         // 사업자등록번호

    @Column(name = "jurir_no")
    private String jurirNo;        // 법인등록번호

    @Column(name = "modify_date")
    private String modifyDate;     // 최종변경일
}