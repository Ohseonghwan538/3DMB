package com.map.egis.service;

import com.map.egis.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MediaServiceTests {
    @TempDir Path temporaryDirectory;

    @Test
    void storesOriginalAndWebpPreview() throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));

        StorageProperties properties = new StorageProperties();
        properties.setRoot(temporaryDirectory);
        MediaService service = new MediaService(properties);
        var result = service.store(new MockMultipartFile("file", "memory.png", "image/png", output.toByteArray()));

        assertTrue(result.originalFilePath().endsWith(".png"));
        assertTrue(result.compressedFilePath().endsWith(".webp"));
        Path preview = temporaryDirectory.resolve("compressed")
                .resolve(result.compressedFilePath().substring("/images/compressed/".length()));
        assertTrue(Files.exists(preview));
        assertNotNull(ImageIO.read(preview.toFile()));
    }

    @Test
    void rejectsUnsupportedAndOversizedUploads() {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(temporaryDirectory);
        MediaService service = new MediaService(properties);

        assertThrows(IllegalArgumentException.class, () -> service.store(
                new MockMultipartFile("file", "image.webp", "image/webp", new byte[]{1, 2, 3})));
        assertThrows(IllegalArgumentException.class, () -> service.store(
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])));
    }

    @Test
    void rejectsContentThatIsNotAnImageEvenWhenMimeTypeClaimsPng() {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(temporaryDirectory);
        MediaService service = new MediaService(properties);

        assertThrows(IllegalArgumentException.class, () -> service.store(
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));
    }

    @Test
    void gpsPreviewReturnsNoLocationForImageWithoutExifGps() throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        StorageProperties properties = new StorageProperties();
        properties.setRoot(temporaryDirectory);
        MediaService service = new MediaService(properties);

        var gps = service.previewGps(new MockMultipartFile("image", "memory.png", "image/png", output.toByteArray()));

        assertNull(gps.latitude());
        assertNull(gps.longitude());
    }
}
