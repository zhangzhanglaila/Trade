package cait.collector.configure;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolExecutorConfigure {
    @Bean("easyspiderCallbackExecutor")
    public ThreadPoolTaskExecutor easyspiderCallbackExecutor(ThreadPoolTaskExecutorBuilder builder) {

        return builder.build();
    }
}
