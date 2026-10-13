package com.digitalgrader.cohorts;

import com.digitalgrader.users.Role;
import com.digitalgrader.users.User;
import org.springframework.stereotype.Component;

@Component("cohortAccess")
public class CohortAccess {
    private final CohortRepository cohorts;
    public CohortAccess(CohortRepository cohorts) { this.cohorts = cohorts; }
    public void requireManage(long id, User.Profile user) {
        boolean allowed = user != null && cohorts.find(id).map(c -> user.role() == Role.ADMIN
                || (user.role() == Role.INSTRUCTOR && c.instructorId() == user.id())).orElse(false);
        if (!allowed) throw new com.digitalgrader.common.ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Access denied");
    }
    public boolean canRead(long id, User.Profile user) {
        if (user == null) return false;
        return cohorts.find(id).map(cohort -> user.role() == Role.ADMIN
                || (user.role() == Role.INSTRUCTOR && cohort.instructorId() == user.id())
                || (user.role() == Role.STUDENT && cohorts.hasStudent(id, user.id()))).orElse(false);
    }
}
