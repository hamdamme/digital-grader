package com.digitalgrader.submissions;

import java.util.List;
import java.util.Map;
import com.digitalgrader.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class SubmissionController {
    private final SubmissionService submissions;
    private final ZipValidator zip;
    public SubmissionController(SubmissionService submissions, ZipValidator zip) { this.submissions=submissions; this.zip=zip; }
    @PostMapping(value="/assignments/{id}/submissions",consumes="multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasRole('STUDENT')")
    public SubmissionService.Accepted submit(@PathVariable long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User.Profile user) {
        submissions.requireStudentAssignment(id,user);
        try (var validated=zip.validate(file)) { return submissions.submit(id,user,validated); }
    }
    @GetMapping("/submissions/{id}")
    public SubmissionService.Submission get(@PathVariable long id, @AuthenticationPrincipal User.Profile user) { return submissions.get(id,user); }
    @GetMapping("/assignments/{id}/mysubmissions") @PreAuthorize("hasRole('STUDENT')")
    public List<SubmissionService.Submission> mine(@PathVariable long id, @AuthenticationPrincipal User.Profile user) { return submissions.mine(id,user); }
    @GetMapping("/submissions/{id}/download")
    public Map<String,Object> download(@PathVariable long id, @AuthenticationPrincipal User.Profile user) { return Map.of("url",submissions.download(id,user),"expiresInSeconds",300); }
}
