package com.maou.apptemplateapi.common.config.ai;

import com.maou.apptemplateapi.common.config.RetrievalProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AgentToolProperties.class, RetrievalProperties.class})
public class AgentToolConfig {
}
