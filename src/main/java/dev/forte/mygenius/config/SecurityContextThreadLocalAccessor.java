package dev.forte.mygenius.config;


import io.micrometer.context.ThreadLocalAccessor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContextThreadLocalAccessor implements ThreadLocalAccessor<SecurityContext> {
    public static final String KEY = "org.springframework.security.context";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public SecurityContext getValue() {
        return SecurityContextHolder.getContext();
    }

    @Override
    public void setValue(SecurityContext value) {
        SecurityContextHolder.setContext(value);
    }

    @Override
    public void reset() {
        SecurityContextHolder.clearContext();
    }
}