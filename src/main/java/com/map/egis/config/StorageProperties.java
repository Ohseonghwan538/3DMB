package com.map.egis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** Root directory for files uploaded by the application. */
    private Path root = Path.of("storage");

    public Path originalDirectory() {
        return root.resolve("original").toAbsolutePath().normalize();
    }

    public Path compressedDirectory() {
        return root.resolve("compressed").toAbsolutePath().normalize();
    }
}
