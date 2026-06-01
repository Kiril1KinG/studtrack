package ru.diploma.studtrack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Disabled("Environment-dependent context test is disabled in unit-test pipeline")
@ActiveProfiles("test")
@SpringBootTest
class StudtrackApplicationTests {

	@Test
	void contextLoads() {
	}

}
