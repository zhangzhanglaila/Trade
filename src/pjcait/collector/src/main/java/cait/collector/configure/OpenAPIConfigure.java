package cait.collector.configure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
@Configuration
public class OpenAPIConfigure {
    private String serverUrl;

    @Value("${wusthelper.docs.server-url:/api}")
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @Bean
    public OpenAPI openAPI() {
        var info = new Info()
                .title("pjcait")
                .description("pjcait api")
                .version("v1");

        var serverList = new ArrayList<Server>(1);
        serverList.add(new Server()
                .url(this.serverUrl)
        );

        return new OpenAPI()
                .servers(serverList)
                .info(info);
    }
}
