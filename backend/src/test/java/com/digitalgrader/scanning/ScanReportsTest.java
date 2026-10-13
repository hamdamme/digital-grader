package com.digitalgrader.scanning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ScanReportsTest {
    private final ObjectMapper json=new ObjectMapper();
    private static final String EMPTY="{\"results\":[],\"errors\":[]}";
    @Test void mapsFindingsWithoutMatchedSourceText() {
        String report="""
            {"results":[{"check_id":"grader-hardcoded-secret","path":"src/Test.java","start":{"line":4},
            "extra":{"message":"Hardcoded credential","lines":"secret-value","metadata":{"cwe":"CWE-798","grader_severity":"CRITICAL"}}}],"errors":[]}
            """;
        var result=ScanReports.parse(json,report,"{\"dependencies\":[]}");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(result.get(0).cweId()).isEqualTo("CWE-798");
        assertThat(result.toString()).doesNotContain("secret-value");
    }
    @Test void mapsDependencyScoresAndDoesNotInventFixedVersions() {
        var findings=ScanReports.parse(json,EMPTY,"""
            {"dependencies":[{"fileName":"library.jar","vulnerabilities":[
            {"name":"CVE-fixture","description":"Fixture only","cvssv3":{"baseScore":9.1},"cwes":["CWE-502"]}]}]}
            """);
        assertThat(findings.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(findings.get(0).fixedVersion()).isNull();
        assertThat(ScanReports.severity(7)).isEqualTo("HIGH");
        assertThat(ScanReports.severity(4)).isEqualTo("MEDIUM");
        assertThat(ScanReports.severity(3.9)).isEqualTo("LOW");
        assertThatThrownBy(() -> ScanReports.severity(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void incompleteReportsNeverBecomeCleanResults() {
        assertThatThrownBy(() -> ScanReports.parse(json,"{\"results\":[],\"errors\":[{}]}","{\"dependencies\":[]}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScanReports.parse(json,EMPTY,"{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScanReports.parse(json,EMPTY,"{\"scanInfo\":{\"analysisExceptions\":[{}]},\"dependencies\":[]}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScanReports.parse(json,EMPTY,null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsPathsThatEscapeTheSubmission() {
        String report="""
            {"results":[{"check_id":"fixture","path":"../secret","start":{"line":1},
            "extra":{"message":"Fixture","metadata":{"cwe":"CWE-798","grader_severity":"CRITICAL"}}}],"errors":[]}
            """;
        assertThatThrownBy(() -> ScanReports.parse(json,report,"{\"dependencies\":[]}")).isInstanceOf(IllegalArgumentException.class);
    }
}
