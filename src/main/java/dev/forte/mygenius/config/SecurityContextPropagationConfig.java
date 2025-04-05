package dev.forte.mygenius.config;
import io.micrometer.context.ThreadLocalAccessor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityContextPropagationConfig {
    @Bean
    public ThreadLocalAccessor<SecurityContext> securityContextAccessor() {
        return new SecurityContextThreadLocalAccessor();
    }

}