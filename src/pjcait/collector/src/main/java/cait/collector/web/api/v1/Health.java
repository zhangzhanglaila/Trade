package cait.collector.web.api.v1;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class Health {
    @RequestMapping("/check")
    public void check(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
