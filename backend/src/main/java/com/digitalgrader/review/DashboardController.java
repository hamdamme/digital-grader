package com.digitalgrader.review;

import java.util.List;
import java.util.Map;
import com.digitalgrader.users.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cohorts")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class DashboardController {
    private final ReviewService reviews;
    public DashboardController(ReviewService reviews) { this.reviews=reviews; }
    @GetMapping("/{id}/dashboard")
    public List<Map<String,Object>> dashboard(@PathVariable long id,@AuthenticationPrincipal User.Profile user) { return reviews.dashboard(id,user); }
    @GetMapping("/{id}/vulnerability-heatmap")
    public List<Map<String,Object>> heatmap(@PathVariable long id,@AuthenticationPrincipal User.Profile user) { return reviews.heatmap(id,user); }
}
