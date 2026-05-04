package com.capgemini.futura.mws.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe in-memory store for mock MWS process states.
 * Also manages retry-error simulation (FKAT_RESOURCENFEHLER_MWS).
 */
@Component
public class MockStateStore {

    private static final Logger logger = LoggerFactory.getLogger(MockStateStore.class);

    /** How many times a processId must call getInfo before a simulated transient error clears */
    private static final int TRANSIENT_ERROR_THRESHOLD = 2;

    private final ConcurrentHashMap<String, ProcessState> processStates = new ConcurrentHashMap<>();

    /** Counts calls to Process_GetInfo per processId - used for transient error simulation */
    private final ConcurrentHashMap<String, AtomicInteger> getInfoCallCount = new ConcurrentHashMap<>();

    /** processIds that should simulate a transient resource error on next getInfo call */
    private final ConcurrentHashMap<String, Boolean> simulateError = new ConcurrentHashMap<>();

    // ===== Process lifecycle =====

    public ProcessState createProcess(String processId) {
        ProcessState state = new ProcessState(processId);
        processStates.put(processId, state);
        logger.info("MockStateStore: created process {}", processId);
        return state;
    }

    public ProcessState getOrCreate(String processId) {
        return processStates.computeIfAbsent(processId, id -> {
            logger.info("MockStateStore: auto-creating process {}", id);
            return new ProcessState(id);
        });
    }

    public ProcessState get(String processId) {
        return processStates.get(processId);
    }

    public void remove(String processId) {
        processStates.remove(processId);
        getInfoCallCount.remove(processId);
        simulateError.remove(processId);
        logger.info("MockStateStore: removed process {}", processId);
    }

    // ===== Transient error simulation =====

    /**
     * Marks a processId to return a simulated resource error on the next N getInfo calls.
     * This lets you test the MWSAdapter retry logic (up to 3 retries, 60s apart).
     */
    public void triggerTransientError(String processId) {
        simulateError.put(processId, Boolean.TRUE);
        getInfoCallCount.put(processId, new AtomicInteger(0));
        logger.info("MockStateStore: transient error armed for process {}", processId);
    }

    /**
     * Returns true if a transient error should be returned for this call,
     * and advances the internal counter (clears after TRANSIENT_ERROR_THRESHOLD calls).
     */
    public boolean shouldSimulateError(String processId) {
        if (!simulateError.getOrDefault(processId, Boolean.FALSE)) {
            return false;
        }
        int count = getInfoCallCount.computeIfAbsent(processId, id -> new AtomicInteger(0))
                .incrementAndGet();
        if (count >= TRANSIENT_ERROR_THRESHOLD) {
            simulateError.remove(processId);
            getInfoCallCount.remove(processId);
            logger.info("MockStateStore: transient error cleared for process {} after {} calls", processId, count);
        }
        return true;
    }
}


