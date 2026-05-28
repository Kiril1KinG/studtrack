package ru.diploma.studtrack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Environment-dependent context test is disabled in unit-test pipeline")
@SpringBootTest
class StudtrackApplicationTests {

	@Test
	void contextLoads() {
	}

}
