package com.reelcipe.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApplicationRoleTest {

    @Test
    void parsesSupportedRoles() {
        assertEquals(ApplicationRole.API, ApplicationRole.parse("api"));
        assertEquals(ApplicationRole.WORKER, ApplicationRole.parse("worker"));
    }

    @Test
    void rejectsUnknownRole() {
        assertThrows(IllegalArgumentException.class, () -> ApplicationRole.parse("scheduler"));
    }
}
