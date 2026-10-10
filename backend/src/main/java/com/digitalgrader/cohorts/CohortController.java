package com.digitalgrader.cohorts;

import java.util.List;
import com.digitalgrader.users.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cohorts")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class CohortController {
    private final CohortService cohorts;
    public CohortController(CohortService cohorts) { this.cohorts=cohorts; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CohortRepository.Cohort create(@Valid @RequestBody CohortService.Create request, @AuthenticationPrincipal User.Profile user) { return cohorts.create(request,user); }
    @PostMapping("/{id}/members") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void members(@PathVariable long id, @Valid @RequestBody CohortService.Members request, @AuthenticationPrincipal User.Profile user) { cohorts.addMembers(id,request,user); }
    @GetMapping("/mine")
    public List<CohortService.Summary> mine(@AuthenticationPrincipal User.Profile user) { return cohorts.mine(user); }
    @GetMapping("/{id}")
    public CohortService.Detail read(@PathVariable long id, @AuthenticationPrincipal User.Profile user) { return cohorts.detail(id,user); }
}
