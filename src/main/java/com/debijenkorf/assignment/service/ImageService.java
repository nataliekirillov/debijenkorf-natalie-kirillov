package com.debijenkorf.assignment.service;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.debijenkorf.assignment.app.configuration.SourceProperties;
import com.debijenkorf.assignment.data.ImageType;
import com.debijenkorf.assignment.enums.ImageTypeEnum;
import com.debijenkorf.assignment.enums.ScaleTypeEnum;
import com.debijenkorf.assignment.exceptions.NotFoundException;
import com.debijenkorf.assignment.strategy.S3DirectoryStrategy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

import static com.debijenkorf.assignment.util.ImageUtil.toBufferedImage;
import static com.debijenkorf.assignment.util.ImageUtil.toByteArray;


/**
 * A service responsible for the retrieval of images
 */
@Service
@Slf4j
public class ImageService {
    private static final String DEFAULT_IMAGE_TYPE = "original";

    private DbLogger dbLog;
    private S3Service s3Service;
    private SourceProperties sourceProperties;
    private S3DirectoryStrategy directoryStrategy;
    private Map<String, ImageType> imageTypes;
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    @PostConstruct
    public void postConstruct() {
        this.imageTypes = Map.of(
                "thumbnail", new ImageType(100, 100, 90, "#FFFFFF", ImageTypeEnum.JPG, ScaleTypeEnum.FILL),
                "fill-red", new ImageType(100, 100, 90, "#FF0000", ImageTypeEnum.JPG, ScaleTypeEnum.FILL),
                "fill-green", new ImageType(100, 100, 90, "#00FF00", ImageTypeEnum.JPG, ScaleTypeEnum.FILL),
                "fill-blue", new ImageType(100, 100, 90, "#0000FF", ImageTypeEnum.JPG, ScaleTypeEnum.FILL),
                "crop", new ImageType(1000, 1000, 90, "#FFFFFF", ImageTypeEnum.JPG, ScaleTypeEnum.CROP),
                "skew", new ImageType(100, 100, 90, "#000000", ImageTypeEnum.JPG, ScaleTypeEnum.SKEW),
                "skew-high", new ImageType(300, 100, 90, "#FF0000", ImageTypeEnum.JPG, ScaleTypeEnum.SKEW),
                DEFAULT_IMAGE_TYPE, new ImageType(0, 0, 100, "#000000", ImageTypeEnum.JPG, ScaleTypeEnum.FILL)
        );
    }

    /**
     * Return an image to the user
     *
     * @param type     Definition type
     * @param filename File path
     * @return Requested image
     */
    public byte[] getImage(String type, String filename) {
        if (!isTypeSupported(type)) {
            String msg = "No predefined type: " + type;
            dbLog.info(msg);
            log.info(msg);
            throw new NotFoundException(msg);
        }

        return getAndStoreS3(type, filename);
    }

    /**
     * Flush an image from S3
     *
     * @param type     Definition type
     * @param filename File path
     */
    public void flushImage(String type, String filename) {
        if (!type.equalsIgnoreCase(DEFAULT_IMAGE_TYPE)) {
            deleteImage(type, filename);
            return;
        }

        imageTypes.keySet().forEach(x -> deleteImage(x, filename));
    }

    /**
     * Get image from S3
     *
     * @return byte[] representing image file from S3
     */
    private byte[] getImageFromS3(String type, String filename) {
        String s3Filepath = directoryStrategy.getDirectoryStrategy(type, filename);
        try {
            InputStream is = s3Service.download(s3Filepath);
            return IOUtils.toByteArray(is);
        } catch (AmazonS3Exception e) {
            log.info("File not found in S3: {}", filename);
            dbLog.info("File not found in S3: " + filename);
        } catch (IOException e) {
            log.error("Failed to get file from S3: {}", e.getMessage());
            log.debug("Failed to get file from S3", e);
            dbLog.error("Failed to get file from S3");
        }

        return new byte[0];
    }

    /**
     * Get Image from S3 and store to S3 if not there
     *
     * @param type     Definition type
     * @param filename Path to file we want to get and store
     * @return The image we want to fetch
     */
    private byte[] getAndStoreS3(String type, String filename) {
        byte [] image = getImageFromS3(type, filename);

        if (image.length == 0) {
            if (type.equalsIgnoreCase(DEFAULT_IMAGE_TYPE)) {
                image = getImageFromSource(filename);
            } else {
                image = getAndStoreS3(DEFAULT_IMAGE_TYPE, filename);
                image = resizeImage(type, image);
            }

            // found image in source - store it
            storeImage(type, filename, image);

            // check that storing worked correctly
            if (!Arrays.equals(image, getImageFromS3(type, filename))) {
                log.error("Failed to get image from S3");
                dbLog.error("Failed to get image from S3");
                throw new NotFoundException("Image not found on source");
            }
        }

        return image;
    }

    /**
     * Get image from source
     *
     * @return byte[] representing image file from source
     */
    private byte[] getImageFromSource(String filename) {
        HttpGet request = new HttpGet(String.join("/", sourceProperties.getRootUrl(), filename));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.NOT_FOUND.value() || HttpStatus.INTERNAL_SERVER_ERROR.value() > 500) {
                dbLog.error("Source URL responded with: " + statusCode);
                log.error("Source URL responded with: {}", statusCode);
                throw new NotFoundException("Source server error: failed to get image from source");
            }
            return response.getEntity().getContent().readAllBytes();
        } catch (IOException e) {
            dbLog.info("Failed to get image from source");
            log.info("Failed to get image from source");
            throw new NotFoundException("Image not found on source");
        }
    }

    /**
     * Delete image(s) from S3
     *
     * @param type     Definition type
     * @param filename The filename we want to delete
     */
    private void deleteImage(String type, String filename) {
        if (!isTypeSupported(type)) {
            String msg = "No predefined type: " + type;
            dbLog.info(msg);
            log.info(msg);
            throw new NotFoundException(msg);
        }

        String s3Filepath = directoryStrategy.getDirectoryStrategy(type, filename);

        try {
            s3Service.delete(s3Filepath);
        } catch (AmazonS3Exception e) {
            String msg = "Failed to delete file: " + filename;
            dbLog.error(msg);
            log.error(msg + ": {}", e.getMessage());
            log.debug(msg, e);
            throw new NotFoundException(msg);
        }
    }

    /**
     * Verify type is defined
     *
     * @param type Definition type
     * @return Is type supported
     */
    private boolean isTypeSupported(String type) {
        return imageTypes.keySet().stream().anyMatch(x -> x.equalsIgnoreCase(type));
    }

    /**
     * Store image to S3
     *
     * @param type     Definition type
     * @param filename Filename we want to store
     * @param image    Byte array representing image
     */
    private void storeImage(String type, String filename, byte[] image){
        String s3Filepath = directoryStrategy.getDirectoryStrategy(type, filename);
        try {
            s3Service.upload(s3Filepath, new ByteArrayInputStream(image));
        } catch (AmazonS3Exception e) {
            dbLog.error("Failed to save image to S3");
            log.error("Failed to save image to S3: {}", e.getMessage());
            log.debug("Failed to save image to S3", e);
            throw new NotFoundException("Failed to store image on S3");
        }
    }

    /**
     * Resize and rescale image
     *
     * @param type  Definition type
     * @param image Byte array representing the image
     * @return New byte array representing resized image
     */
    private byte[] resizeImage(String type, byte[] image) {
        ImageType imageType = imageTypes.get(type.toLowerCase());

        try {
            Image resizedImage = getResizedImage(imageType, toBufferedImage(image));

            // the requested size of the image
            BufferedImage target = new BufferedImage(imageType.getWidth(), imageType.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            // determine the buffers that are going to be filled / cropped depending on the requested type
            int widthBuffer = Math.abs((resizedImage.getWidth(null) - target.getWidth()) / 2);
            int heightBuffer = Math.abs((resizedImage.getHeight(null) - target.getHeight()) / 2);

            Graphics2D graphics = target.createGraphics();
            switch (imageType.getScaleType()) {
                // draw the resized image without buffers in the target
                case CROP -> graphics.drawImage(resizedImage, 0, 0, target.getWidth(), target.getHeight(),
                        widthBuffer, heightBuffer, resizedImage.getWidth(null) - widthBuffer,
                        resizedImage.getHeight(null) - heightBuffer, null);
                // draw the resized image in the target and fill the buffers
                case FILL -> {
                    graphics.drawImage(resizedImage, widthBuffer, heightBuffer,
                            target.getWidth() - widthBuffer, target.getHeight() - heightBuffer, 0, 0,
                            resizedImage.getWidth(null), resizedImage.getHeight(null), null);
                    graphics.setColor(Color.decode(imageType.getFillColor()));

                    // color the buffers
                    if (widthBuffer == 0) {
                        graphics.fillRect(0, 0, target.getWidth(), heightBuffer);
                        graphics.fillRect(0, target.getHeight() - heightBuffer, target.getWidth(), target.getHeight());
                    } else {
                        graphics.fillRect(0, 0, widthBuffer, target.getHeight());
                        graphics.fillRect(target.getWidth() - widthBuffer, 0, target.getWidth(), target.getHeight());
                    }
                }
                // draw the resized image in the target
                case SKEW -> graphics.drawImage(resizedImage, 0, 0, target.getWidth(), target.getHeight(), 0, 0,
                        resizedImage.getWidth(null), resizedImage.getHeight(null), null);
            }

            // return the image in the requested format (jpg/png etc.)
            return toByteArray(target, imageType.getType().toString());
        } catch (IOException e) {
            String msg = "Failed to resize image";
            log.error(msg + ": {}", e.getMessage());
            log.debug(msg, e);
            dbLog.error(msg);
            throw new NotFoundException(msg);
        }
    }

    /**
     * Resize the image according to the requested type.
     * For CROP -> resize the image to the minimum size that contains the target size
     * For FILL -> resize the image to the maximum size that's contained in the target size
     * For SKEW -> take the image and stretch it according to the target size
     *
     * @param imageType Image type by definition type
     * @param origin    Original image
     * @return Image object defining the new resized image
     */
    private Image getResizedImage(ImageType imageType, BufferedImage origin) {
        Image resultingImage;

        boolean wider = (imageType.getWidth() / (double) imageType.getHeight()) >= (origin.getWidth() / (double) origin.getHeight());
        boolean crop = imageType.getScaleType().equals(ScaleTypeEnum.CROP);
        boolean fill = imageType.getScaleType().equals(ScaleTypeEnum.FILL);

        if ((crop && wider) || (fill && !wider)) {
            // CROP && WIDER - keep the ratio and match the width
            // FILL && HIGHER - keep the ratio and match the width
            resultingImage = origin.getScaledInstance(imageType.getWidth(), -1, Image.SCALE_DEFAULT);
        } else if (crop || fill) {
            // CROP && HIGHER - keep the ratio and match the height
            // FILL && WIDER - keep the ratio and match the height
            resultingImage = origin.getScaledInstance(-1, imageType.getHeight(), Image.SCALE_DEFAULT);
        } else {
            // SKEW - fits the original image to the requested width and height
            resultingImage = origin.getScaledInstance(imageType.getWidth(), imageType.getHeight(), Image.SCALE_DEFAULT);
        }

        return resultingImage;
    }

    @Autowired
    public void setSourceProperties(SourceProperties sourceProperties) {
        this.sourceProperties = sourceProperties;
    }

    @Autowired
    public void setS3Service(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @Autowired
    public void setDbLog(DbLogger dbLog) {
        this.dbLog = dbLog;
    }

    @Autowired
    public void setDirectoryStrategy(S3DirectoryStrategy directoryStrategy) {
        this.directoryStrategy = directoryStrategy;
    }
}
