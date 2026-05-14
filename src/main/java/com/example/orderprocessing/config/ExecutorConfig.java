package com.example.orderprocessing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Spring configuration that exposes the virtual-thread executor used by the
 * order-processing pipeline for any fan-out work (e.g., parallel notification
 * channel delivery).
 *
 * <p>Virtual threads for Tomcat request handling are enabled separately via
 * {@code spring.threads.virtual.enabled=true} in {@code application.yml}
 * (Requirement 11.1). This bean covers the internal pipeline executor
 * (Requirement 11.2).
 */
@Configuration
public class ExecutorConfig {

    /**
     * Produces a virtual-thread-per-task {@link Executor} bean named
     * {@code pipelineExecutor}.
     *
     * <p>Each submitted task runs on a fresh Java 21 virtual thread, so blocking
     * I/O inside pipeline stages (e.g., notification dispatch) does not consume
     * platform threads. The executor is unbounded — back-pressure is applied at
     * the per-{@code OrderId} lock level by {@code OrderLockRegistry}.
     *
     * @return an {@link Executor} backed by {@link Executors#newVirtualThreadPerTaskExecutor()}
     */
    @Bean(name = "pipelineExecutor")
    public Executor pipelineExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
