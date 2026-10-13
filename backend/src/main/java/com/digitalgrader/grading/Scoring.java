package com.digitalgrader.grading;

import java.math.*;
import java.util.*;
import com.digitalgrader.assignments.Rubric;

public final class Scoring {
    public record Score(BigDecimal score, BigDecimal tests, BigDecimal coverage, BigDecimal quality) {}
    private Scoring() {}
    public static List<ReportParser.TestCase> markFlaky(List<ReportParser.TestCase> first,List<ReportParser.TestCase> retry) {
        var passed=new HashSet<String>();
        retry.stream().filter(t -> t.status().equals("PASSED")).forEach(t -> passed.add(t.name()));
        return first.stream().map(t -> t.status().equals("FAILED") && passed.contains(t.name())
                ? new ReportParser.TestCase(t.name(),"FLAKY",t.durationMs(),t.message(),t.stackTrace()) : t).toList();
    }
    public static Score calculate(Rubric rubric,List<ReportParser.TestCase> tests,ReportParser.Coverage coverage,
            Map<String,String> qualityReports,boolean compiled) {
        // Skipped/flaky tests do not earn pass credit. No reports means zero, never full credit.
        long passed=tests.stream().filter(t -> t.status().equals("PASSED")).count();
        BigDecimal testPct=tests.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(passed*100).divide(BigDecimal.valueOf(tests.size()),4,RoundingMode.HALF_UP);
        boolean complete=compiled; long violations=0;
        for (String tool:rubric.qualityChecks()) {
            if (!qualityReports.containsKey(tool)) complete=false;
            else violations+=ReportParser.violations(qualityReports.get(tool),tool);
        }
        BigDecimal quality=complete ? BigDecimal.valueOf(Math.max(0,100-5*violations)) : BigDecimal.ZERO;
        if (!compiled) return new Score(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        BigDecimal value=testPct.multiply(BigDecimal.valueOf(rubric.weights().tests()))
                .add(coverage.line().multiply(BigDecimal.valueOf(rubric.weights().coverage())))
                .add(quality.multiply(BigDecimal.valueOf(rubric.weights().codeQuality()))).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
        return new Score(value,testPct,coverage.line(),quality);
    }
}
