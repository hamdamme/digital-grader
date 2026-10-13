package com.digitalgrader.review;

import java.util.List;
import java.util.Map;
import com.digitalgrader.users.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/submissions")
public class ReviewController {
    private final ReviewService reviews;
    public ReviewController(ReviewService reviews) { this.reviews=reviews; }
    @GetMapping
    public List<Map<String,Object>> list(@RequestParam long cohortId,@AuthenticationPrincipal User.Profile user) {
        return reviews.list(cohortId,user);
    }
    @PostMapping("/{id}/overrides") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public void override(@PathVariable long id,@Valid @RequestBody ReviewService.OverrideRequest request,@AuthenticationPrincipal User.Profile user) {
        reviews.override(id,request,user);
    }
}
