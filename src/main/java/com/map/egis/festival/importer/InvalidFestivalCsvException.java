package com.map.egis.festival.importer;

public class InvalidFestivalCsvException extends RuntimeException {
    public InvalidFestivalCsvException(String message) {
        super(message);
    }

    public InvalidFestivalCsvException(String message, Throwable cause) {
        super(message, cause);
    }
}
