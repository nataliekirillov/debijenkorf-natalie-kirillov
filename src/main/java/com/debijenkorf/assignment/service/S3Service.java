package com.debijenkorf.assignment.service;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.debijenkorf.assignment.app.configuration.S3Properties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * A service responsible for the communication with S3
 */
@Service
@Slf4j
public class S3Service implements StorageService {
    private S3Properties s3Properties;
    private AmazonS3 s3client;

    /**
     * Establish the S3 Client connection after the bean has initialized
     */
    @PostConstruct
    public void postConstruct() {
        AWSCredentials credentials = new BasicAWSCredentials(s3Properties.getAccessKey(), s3Properties.getSecretKey());
        this.s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(s3Properties.getRegion())
                .build();
    }

    /**
     * Download file from S3 bucket
     *
     * @param path The file path we want to download
     * @return InputStream of the file we have downloaded
     */
    @Override
    public InputStream download(String path) {
        S3Object object = s3client.getObject(s3Properties.getBucket(), path);
        return object.getObjectContent();
    }

    /**
     * Upload file to S3 bucket
     *
     * @param path The location we want to upload the file to
     * @param is   InputStream that we want to upload
     */
    @Override
    public void upload(String path, InputStream is) {
        ObjectMetadata metaData = new ObjectMetadata();
        PutObjectRequest putOb = new PutObjectRequest(s3Properties.getBucket(), path, is, metaData);
        s3client.putObject(putOb);
    }

    /**
     * Delete file from S3 bucket
     *
     * @param path The file path we want to delete
     */
    @Override
    public void delete(String path) {
        s3client.deleteObject(s3Properties.getBucket(), path);
    }

    @Autowired
    public void setS3Properties(S3Properties s3Properties) {
        this.s3Properties = s3Properties;
    }
}
