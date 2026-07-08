package com.medilabo.assessmentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "gateway.secret=test-secret")
class AssessmentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
