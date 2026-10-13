package com.digitalgrader.scanning;

import com.fasterxml.jackson.databind.*;
import java.util.*;

public final class ScanReports {
    public record Finding(String source,String ruleId,String cweId,String severity,String filePath,Integer lineNumber,
                          String message,String fixGuidance,String dependencyName,String currentVersion,String fixedVersion) {}
    private static final Set<String> SEVERITIES=Set.of("CRITICAL","HIGH","MEDIUM","LOW");
    private ScanReports() {}
    public static List<Finding> parse(ObjectMapper json,String semgrep,String dependencies) {
        try {
            var findings=new LinkedHashSet<Finding>();
            var staticReport=read(json,semgrep);
            if (!staticReport.path("results").isArray() || !staticReport.path("errors").isArray() || !staticReport.path("errors").isEmpty())
                throw new IllegalArgumentException("Incomplete static analysis");
            for (var result:staticReport.path("results")) {
                var extra=result.path("extra"); var metadata=extra.path("metadata");
                String severity=required(metadata,"grader_severity",10);
                if (!SEVERITIES.contains(severity)) throw new IllegalArgumentException("Unknown severity");
                String cwe=required(metadata,"cwe",20);
                if (!cwe.matches("CWE-[0-9]+")) throw new IllegalArgumentException("Invalid CWE");
                int line=result.path("start").path("line").asInt(0);
                if (line<1) throw new IllegalArgumentException("Invalid source line");
                findings.add(new Finding("SEMGREP",required(result,"check_id",255),cwe,severity,path(result,"path"),line,
                        required(extra,"message",2000),optional(metadata,"guidance",1000),null,null,null));
                limit(findings);
            }
            var dependencyReport=read(json,dependencies);
            if (!dependencyReport.path("dependencies").isArray() || dependencyReport.hasNonNull("scanInfo") && !dependencyReport.path("scanInfo").path("analysisExceptions").isMissingNode() && !dependencyReport.path("scanInfo").path("analysisExceptions").isEmpty())
                throw new IllegalArgumentException("Incomplete dependency analysis");
            for (var dependency:dependencyReport.path("dependencies")) {
                var vulnerabilities=dependency.path("vulnerabilities");
                if (vulnerabilities.isMissingNode()) continue;
                if (!vulnerabilities.isArray()) throw new IllegalArgumentException("Invalid vulnerabilities");
                for (var vulnerability:vulnerabilities) {
                    var score=vulnerability.path("cvssv4").path("baseScore");
                    if (!score.isNumber()) score=vulnerability.path("cvssv3").path("baseScore");
                    if (!score.isNumber()) score=vulnerability.path("cvssv2").path("score");
                    if (!score.isNumber()) throw new IllegalArgumentException("Missing CVSS score");
                    String cwe=null;
                    if (vulnerability.path("cwes").isArray() && !vulnerability.path("cwes").isEmpty()) {
                        cwe=vulnerability.path("cwes").get(0).asText();
                        if (!cwe.matches("CWE-[0-9]{1,10}")) cwe=null;
                    }
                    // Keep scanner descriptions, not source snippets or matched credential values.
                    findings.add(new Finding("DEPENDENCY_CHECK",required(vulnerability,"name",255),cwe,severity(score.asDouble()),
                            path(dependency,"fileName"),null,required(vulnerability,"description",2000),null,
                            required(dependency,"fileName",255),null,null));
                    limit(findings);
                }
            }
            return List.copyOf(findings);
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid scan JSON",e); }
    }
    public static String severity(double score) {
        if (!Double.isFinite(score) || score<0 || score>10) throw new IllegalArgumentException("Invalid CVSS score");
        return score>=9?"CRITICAL":score>=7?"HIGH":score>=4?"MEDIUM":"LOW";
    }
    private static JsonNode read(ObjectMapper json,String content) throws java.io.IOException {
        if (content==null || content.length()>4_000_000) throw new IllegalArgumentException("Missing or oversized report");
        var node=json.readTree(content);
        if (node==null || !node.isObject()) throw new IllegalArgumentException("Invalid report object");
        return node;
    }
    private static String path(JsonNode node,String field) {
        String value=required(node,field,512).replace('\\','/');
        if (value.startsWith("/code/project/")) value=value.substring(14);
        if (value.startsWith("/") || value.contains(":") || Arrays.stream(value.split("/",-1)).anyMatch(p -> p.isBlank() || p.equals("..") || p.equals(".")) || value.chars().anyMatch(c -> c<32))
            throw new IllegalArgumentException("Invalid report path");
        return value;
    }
    private static String required(JsonNode node,String key,int max) {
        var value=node.path(key);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length()>max) throw new IllegalArgumentException("Invalid report field");
        return value.asText();
    }
    private static String optional(JsonNode node,String key,int max) { return node.hasNonNull(key)?required(node,key,max):null; }
    private static void limit(Set<Finding> findings) { if (findings.size()>1000) throw new IllegalArgumentException("Too many findings"); }
}
