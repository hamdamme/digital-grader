export type Role = "STUDENT" | "INSTRUCTOR" | "ADMIN";
export type User = { id: number; fullName: string; email: string; role: Role };
export type Cohort = {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  studentCount: number;
};
export type Assignment = {
  id: number;
  cohortId: number;
  title: string;
  description: string | null;
  dueDate: string | null;
  maxAttempts: number;
  rubricJson: {
    weights: { tests: number; coverage: number; codeQuality: number };
    coverageThreshold: number;
    qualityChecks: string[];
  };
};
export type Submission = {
  id: number;
  assignmentId: number;
  studentId: number;
  attemptNumber: number;
  status: "QUEUED" | "RUNNING" | "GRADED" | "FAILED" | "TIMEOUT";
  submittedAt: string;
  score: number | null;
  failureReason?: string | null;
};
export type ReviewRow = Submission & {
  assignmentTitle: string;
  studentName: string;
  automatedScore: number | null;
  coverageLinePct: number | null;
  findingCount: number | null;
  securityStatus: string;
};
export type Hint = { content: string; model: string };
export type Result = {
  submissionId: number;
  buildSuccess: boolean;
  testsTotal: number;
  testsPassed: number;
  testsFailed: number;
  testsFlaky: number;
  coverageLinePct: number;
  coverageBranchPct: number;
  score: number;
  effectiveScore: number;
  securityStatus: string;
  testCaseResults: {
    testName: string;
    status: string;
    durationMs: number;
    errorMessage: string | null;
    stackTrace: string | null;
  }[];
  securityFindings: {
    source: string;
    ruleId: string;
    cweId: string | null;
    severity: string;
    filePath: string | null;
    lineNumber: number | null;
    message: string;
    fixGuidance: string | null;
  }[];
  hints: Hint[];
  overrides: {
    id: number;
    originalScore: number;
    score: number;
    reason: string;
    instructorName: string;
    createdAt: string;
  }[];
};
