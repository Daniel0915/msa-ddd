package com.back.global.global;

import com.back.global.eventPublisher.EventPublisher;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GlobalConfig {
    @Getter
    private static EventPublisher eventPublisher;

    @Autowired
    public void setEVentPublisher(EventPublisher eventPublisher) { GlobalConfig.eventPublisher = eventPublisher; }
}
