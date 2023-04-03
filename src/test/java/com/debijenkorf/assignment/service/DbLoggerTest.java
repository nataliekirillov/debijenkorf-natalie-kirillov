package com.debijenkorf.assignment.service;

import com.debijenkorf.assignment.repository.LogDBRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DbLoggerTest {
    private DbLogger dbLogger;
    private AutoCloseable autoCloseable;
    @Mock private LogDBRepository logDBRepository;

    @BeforeEach
    public void setUp() {
        this.autoCloseable = MockitoAnnotations.openMocks(this);
        this.dbLogger = new DbLogger();
        this.dbLogger.setLogRepository(logDBRepository);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testInfo() {
        dbLogger.info("info-log");
        verify(logDBRepository, times(1)).insert(refEq("INFO"), refEq("info-log"));
    }

    @Test
    void testWarn() {
        dbLogger.warn("warn-log");
        verify(logDBRepository, times(1)).insert(refEq("WARN"), refEq("warn-log"));
    }

    @Test
    void testError() {
        dbLogger.error("error-log");
        verify(logDBRepository, times(1)).insert(refEq("ERROR"), refEq("error-log"));
    }

    @Test
    void testDebug() {
        dbLogger.debug("debug-log");
        verify(logDBRepository, times(1)).insert(refEq("DEBUG"), refEq("debug-log"));
    }
}