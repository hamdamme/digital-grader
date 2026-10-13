package com.digitalgrader.assignments;

import java.util.List;
import com.digitalgrader.users.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {
    private final AssignmentService assignments;
    public AssignmentController(AssignmentService assignments) { this.assignments=assignments; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public AssignmentService.Assignment create(@Valid @RequestBody AssignmentService.Create request, @AuthenticationPrincipal User.Profile user) { return assignments.create(request,user); }
    @GetMapping
    public List<AssignmentService.Assignment> list(@RequestParam long cohortId, @AuthenticationPrincipal User.Profile user) { return assignments.list(cohortId,user); }
    @GetMapping("/{id}")
    public AssignmentService.Assignment get(@PathVariable long id, @AuthenticationPrincipal User.Profile user) { return assignments.get(id,user); }
}
