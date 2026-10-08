package com.map.egis.service;

import com.map.egis.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png");
    private final StorageProperties storageProperties;

    static { ImageIO.scanForPlugins(); }

    public Gps previewGps(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE || !TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("JPEG 또는 PNG 형식의 5MB 이하 사진만 등록할 수 있습니다.");
        }
        try (InputStream input = file.getInputStream()) {
            return gps(ImageMetadataReader.readMetadata(input));
        } catch (Exception error) {
            throw new IllegalArgumentException("사진의 GPS 정보를 읽을 수 없습니다.", error);
        }
    }

    public UploadedImage store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE || !TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("JPEG 또는 PNG 형식의 5MB 이하 사진만 등록할 수 있습니다.");
        }

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String sourceName = UUID.randomUUID() + ".upload";
        Path source = storageProperties.originalDirectory().resolve(date).resolve(sourceName);
        String extension = "jpg";
        Path original = null;
        String previewName = UUID.randomUUID() + ".webp";
        Path compressed = storageProperties.compressedDirectory().resolve(date).resolve(previewName);

        try {
            Files.createDirectories(source.getParent());
            Files.createDirectories(compressed.getParent());
            file.transferTo(source);
            BufferedImageInfo imageInfo = inspect(source);
            extension = imageInfo.format();
            Gps gps = gps(source);
            original = storageProperties.originalDirectory().resolve(date).resolve(UUID.randomUUID() + "." + extension);
            normalize(source, original, extension, imageInfo.orientation());
            Thumbnails.of(original.toFile()).size(1280, 1280).outputFormat("webp").outputQuality(0.82).toFile(compressed.toFile());
            return new UploadedImage("/images/original/" + date + "/" + original.getFileName(),
                    "/images/compressed/" + date + "/" + previewName, gps.latitude(), gps.longitude(), gps.altitude());
        } catch (IOException | RuntimeException error) {
            delete(source); delete(original); delete(compressed);
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalStateException("사진을 저장하는 중 문제가 발생했습니다.", error);
        } finally {
            delete(source);
        }
    }

    public void remove(UploadedImage image) {
        if (image == null) return;
        delete(resolve(image.originalFilePath(), "/images/original/", storageProperties.originalDirectory()));
        delete(resolve(image.compressedFilePath(), "/images/compressed/", storageProperties.compressedDirectory()));
    }

    private Path resolve(String stored, String prefix, Path root) {
        if (stored == null || !stored.startsWith(prefix)) return null;
        Path path = root.resolve(stored.substring(prefix.length())).normalize();
        return path.startsWith(root) ? path : null;
    }

    private BufferedImageInfo inspect(Path file) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(file)) { header = input.readNBytes(8); }
        String format;
        if (header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8) {
            format = "jpg";
        } else if (header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P'
                && header[2] == 'N' && header[3] == 'G') {
            format = "png";
        } else {
            throw new IllegalArgumentException("JPEG 또는 PNG 이미지 파일만 등록할 수 있습니다.");
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("열 수 없는 이미지 파일입니다.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS || width > 20_000 || height > 20_000) {
                    throw new IllegalArgumentException("이미지 해상도가 허용 범위를 초과합니다.");
                }
                if (reader.read(0) == null) throw new IllegalArgumentException("열 수 없는 이미지 파일입니다.");
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (IOException | RuntimeException invalidImage) {
            throw new IllegalArgumentException("열 수 없는 이미지 파일입니다.", invalidImage);
        }
        int orientation = 1;
        try {
            ExifIFD0Directory directory = ImageMetadataReader.readMetadata(file.toFile()).getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {
            // Orientation metadata is optional; invalid metadata does not invalidate a decodable image.
        }
        return new BufferedImageInfo(format, orientation);
    }

    private void normalize(Path source, Path target, String format, int orientation) throws IOException {
        int rotation = switch (orientation) {
            case 3, 4 -> 180;
            case 5, 6 -> 90;
            case 7, 8 -> 270;
            default -> 0;
        };
        Thumbnails.Builder<?> builder = Thumbnails.of(source.toFile()).scale(1.0);
        if (rotation != 0) builder.rotate(rotation);
        builder.outputFormat(format).toFile(target.toFile());
    }

    private void delete(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            log.warn("Unable to remove temporary or rolled-back image file {}", path, error);
        }
    }
    private Gps gps(Path file) {
        try {
            return gps(ImageMetadataReader.readMetadata(file.toFile()));
        } catch (Exception ignored) { }
        return new Gps(null, null, null);
    }

    private Gps gps(Metadata metadata) throws Exception {
        GpsDirectory directory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (directory == null || directory.getGeoLocation() == null) return new Gps(null, null, null);
        double latitude = directory.getGeoLocation().getLatitude();
        double longitude = directory.getGeoLocation().getLongitude();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return new Gps(null, null, null);
        }
        Double altitude = null;
        if (directory.containsTag(GpsDirectory.TAG_ALTITUDE)) {
            altitude = directory.getRational(GpsDirectory.TAG_ALTITUDE).doubleValue();
            if (directory.containsTag(GpsDirectory.TAG_ALTITUDE_REF) && directory.getInt(GpsDirectory.TAG_ALTITUDE_REF) == 1) {
                altitude = -altitude;
            }
        }
        return new Gps(latitude, longitude, altitude == null || !Double.isFinite(altitude) ? 0D : altitude);
    }
    private record BufferedImageInfo(String format, int orientation) { }
    public record Gps(Double latitude, Double longitude, Double altitude) { }
    public record UploadedImage(String originalFilePath, String compressedFilePath, Double latitude, Double longitude, Double altitude) { }
}
