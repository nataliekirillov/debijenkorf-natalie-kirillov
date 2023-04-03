package com.debijenkorf.assignment.service;

import com.debijenkorf.assignment.app.configuration.SourceProperties;
import com.debijenkorf.assignment.exceptions.NotFoundException;
import com.debijenkorf.assignment.strategy.S3DirectoryStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageServiceTest {
    private ImageService imageService;
    private AutoCloseable autoCloseable;

    @Mock private DbLogger dbLog;
    @Mock private S3Service s3Service;
    @Mock private SourceProperties sourceProperties;

    @BeforeEach
    public void setUp() {
        S3DirectoryStrategy directoryStrategy = new S3DirectoryStrategy();
        this.autoCloseable = MockitoAnnotations.openMocks(this);
        this.imageService = new ImageService();
        this.imageService.postConstruct();
        imageService.setDbLog(dbLog);
        imageService.setS3Service(s3Service);
        imageService.setSourceProperties(sourceProperties);
        imageService.setDirectoryStrategy(directoryStrategy);
    }

    @Test
    void testGetImageThrowsNotFoundException() {
        String type = "not-supported";
        assertThrows(NotFoundException.class, () -> imageService.getImage(type, "/filename.jpg"));
        verify(dbLog, times(1)).info("No predefined type: " + type);
    }

    @Test
    void testGetImageFromS3ExistsInS3() {
        String type = "thumbnail";
        String filename = "filename.jpg";

        InputStream is = this.getClass().getResourceAsStream("/images/fill-green.jpeg");
        when(s3Service.download("thumbnail/file/filename.jpg")).thenReturn(is);

        byte[] result = imageService.getImage(type, filename);
        assertNotNull(result);
        assertTrue(result.length > 0);
        verify(s3Service, times(1)).download("thumbnail/file/filename.jpg");
    }

    @Test
    void testFlushImageNotOriginal() {
        String type = "thumbnail";
        String filename = "filename.jpg";

        this.imageService.flushImage(type, filename);
        verify(s3Service, times(1)).delete("thumbnail/file/filename.jpg");
        verify(s3Service, times(0)).delete("fill-red/file/filename.jpg");
        verify(s3Service, times(0)).delete("fill-green/file/filename.jpg");
        verify(s3Service, times(0)).delete("fill-blue/file/filename.jpg");
        verify(s3Service, times(0)).delete("crop/file/filename.jpg");
        verify(s3Service, times(0)).delete("skew/file/filename.jpg");
        verify(s3Service, times(0)).delete("skew-high/file/filename.jpg");
    }

    @Test
    void testFlushImageOriginal() {
        String type = "original";
        String filename = "filename.jpg";

        this.imageService.flushImage(type, filename);
        verify(s3Service, times(1)).delete("thumbnail/file/filename.jpg");
        verify(s3Service, times(1)).delete("fill-red/file/filename.jpg");
        verify(s3Service, times(1)).delete("fill-green/file/filename.jpg");
        verify(s3Service, times(1)).delete("fill-blue/file/filename.jpg");
        verify(s3Service, times(1)).delete("crop/file/filename.jpg");
        verify(s3Service, times(1)).delete("skew/file/filename.jpg");
        verify(s3Service, times(1)).delete("skew-high/file/filename.jpg");
    }

    @AfterEach
    public void tearDown() throws Exception {
        this.autoCloseable.close();
    }
}