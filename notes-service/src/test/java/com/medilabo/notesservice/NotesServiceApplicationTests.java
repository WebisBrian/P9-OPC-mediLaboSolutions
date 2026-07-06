package com.medilabo.notesservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "gateway.secret=test-secret")
class NotesServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
