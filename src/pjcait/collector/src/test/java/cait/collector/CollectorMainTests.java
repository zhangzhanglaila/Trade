package cait.collector;

import okhttp3.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

class CollectorMainTests {

	@Test
	void contextLoads() {
        MediaType.get("application/json; charset=utf-8");
	}

}
