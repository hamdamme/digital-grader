package com.digitalgrader.feedback;

import com.digitalgrader.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/submissions")
public class HintController {
    private final HintService hints;
    public HintController(HintService hints) { this.hints=hints; }
    @PostMapping("/{id}/hint")
    @ResponseStatus(HttpStatus.CREATED)
    public HintRepository.Hint generate(@PathVariable long id,@AuthenticationPrincipal User.Profile user) { return hints.generate(id,user); }
}
