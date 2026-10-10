package com.digitalgrader;

import java.math.BigDecimal;
import java.util.*;
import com.digitalgrader.grading.*;
import com.digitalgrader.assignments.Rubric;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GradingUnitTest {
    @Test void parsesPassFailureErrorAndSkip() {
        var tests=ReportParser.junit(List.of("<testsuite><testcase classname='C' name='pass' time='0.123'/><testcase classname='C' name='fail'><failure message='expected'>trace</failure></testcase><testcase name='error'><error/></testcase><testcase name='skip'><skipped/></testcase></testsuite>"));
        assertThat(tests).extracting(ReportParser.TestCase::status).containsExactly("PASSED","FAILED","FAILED","SKIPPED");
        assertThat(tests.get(0).durationMs()).isEqualTo(123);
        assertThat(tests.get(1).message()).isEqualTo("expected");
    }
    @Test void rejectsEntitiesMalformedReportsAndDuplicateIdentities() {
        assertThatThrownBy(() -> ReportParser.junit(List.of("<!DOCTYPE testsuite [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><testsuite>&secret;</testsuite>"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportParser.junit(List.of("<testsuite>"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportParser.junit(List.of("<testsuite><testcase name='same'/><testcase name='same'/></testsuite>"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void readsOnlyAggregateCoverageAndAllowsJacocoDoctypeWithoutFetchingIt() {
        var c=ReportParser.jacoco("<!DOCTYPE report PUBLIC '-//JACOCO//DTD Report 1.1//EN' 'report.dtd'><report><package><counter type='LINE' missed='0' covered='999'/></package><counter type='LINE' missed='1' covered='3'/><counter type='BRANCH' missed='0' covered='0'/></report>");
        assertThat(c.line()).isEqualByComparingTo("75"); assertThat(c.branch()).isEqualByComparingTo("0");
        assertThat(ReportParser.jacoco(null).line()).isZero();
    }
    @Test void flakyMatchingPreservesFirstRunFailuresAndNoFreePassCredit() {
        var first=ReportParser.junit(List.of("<testsuite><testcase name='a'><failure/></testcase><testcase name='b'/></testsuite>"));
        var retry=ReportParser.junit(List.of("<testsuite><testcase name='a'/></testsuite>"));
        var flaky=Scoring.markFlaky(first,retry);
        assertThat(flaky).extracting(ReportParser.TestCase::status).containsExactly("FLAKY","PASSED");
        var rubric=new Rubric(new Rubric.Weights(60,20,20),70,List.of("checkstyle"));
        var score=Scoring.calculate(rubric,flaky,new ReportParser.Coverage(new BigDecimal("75"),BigDecimal.ZERO),Map.of("checkstyle","<checkstyle><file><error/></file></checkstyle>"),true);
        assertThat(score.score()).isEqualByComparingTo("64");
        assertThat(Scoring.calculate(rubric,List.of(),ReportParser.jacoco(null),Map.of(),false).score()).isZero();
        assertThat(Scoring.calculate(rubric,List.of(),ReportParser.jacoco(null),Map.of(),true).quality()).isZero();
    }
    @Test void refusesToRunWithoutGvisor() {
        var docker=mock(DockerCommand.class);
        when(docker.run(anyList(),any(),anyInt())).thenReturn(new DockerCommand.Output(0,"{\"runc\":{}}"));
        var sandbox=new DockerSandbox(docker,new ObjectMapper(),"digital-grader-student:session6",30,1536,1);
        assertThatThrownBy(sandbox::verifyReady).hasMessageContaining("runsc");
        assertThatThrownBy(() -> sandbox.run(java.nio.file.Path.of("fixture.zip"))).hasMessageContaining("readiness");
        verify(docker,times(1)).run(anyList(),any(),anyInt());
    }
}
