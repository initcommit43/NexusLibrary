package dev.nexus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
// Achievement syncs make one Steam call per game, which is far too slow to hold a request
// open for; they run on a background executor and are polled instead.
@EnableAsync
public class SchedulingConfig {}
