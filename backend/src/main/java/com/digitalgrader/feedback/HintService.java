package com.digitalgrader.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digitalgrader.common.ApiException;
import com.digitalgrader.submissions.SubmissionService;
import com.digitalgrader.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HintService {
    private final SubmissionService submissions;
    private final HintRepository hints;
    private final HintProvider provider;
    private final ObjectMapper json;
    public HintService(SubmissionService submissions,HintRepository hints,HintProvider provider,ObjectMapper json) { this.submissions=submissions;this.hints=hints;this.provider=provider;this.json=json; }
    public HintRepository.Hint generate(long id,User.Profile user) {
        submissions.get(id,user);
        var cached=hints.cached(id);
        if (cached.isPresent()) return cached.get();
        var token=hints.claim(id);
        try {
            var reply=provider.generate(json.writeValueAsString(hints.context(id)));
            String content=ChatHintProvider.validate(reply.content());
            if (reply.model()==null || reply.model().isBlank() || reply.model().length()>100) throw new IllegalArgumentException();
            return hints.finish(id,token,new HintProvider.Reply(content,reply.model()));
        } catch (Exception error) {
            hints.release(id,token);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Hint service is unavailable; try again later");
        }
    }
}
