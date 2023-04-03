package com.debijenkorf.assignment.service;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {
    /**
     * Download from storage supplier
     *
     * @param path The location of the file we want to download
     * @return InputStream representing the file
     */
    InputStream download(String path);

    /**
     * Upload a file to the storage supplier
     *
     * @param path The location we want to upload to
     * @param is   Stream representing the file
     */
    @Retryable(retryFor = IOException.class, maxAttempts = 1, backoff = @Backoff(delay = 200))
    void upload(String path, InputStream is) throws IOException;

    /**
     * Delete file from storage supplier
     *
     * @param path The location of the file we want to delete
     */
    void delete(String path);
}
