package com.debijenkorf.assignment.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ImageUtilTest {
    @Test
    void testToByteArray() throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream("/images/fill-green.jpeg")) {
            assertNotNull(is);
            BufferedImage image = ImageIO.read(is);

            byte[] imageBytes = ImageUtil.toByteArray(image, "jpg");
            assertNotNull(imageBytes);
            assertTrue(imageBytes.length > 0);
        };
    }

    @Test
    void testToBufferedImage() throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream("/images/fill-green.jpeg")) {
            assertNotNull(is);
            byte[] imageBytes = is.readAllBytes();

            BufferedImage bufferedImage = ImageUtil.toBufferedImage(imageBytes);
            assertNotNull(bufferedImage);
        }
    }
}