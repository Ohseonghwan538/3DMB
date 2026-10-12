package com.map.egis.festival.importer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FestivalCsvReaderTests {
    private final FestivalCsvReader reader = new FestivalCsvReader();

    @Test
    void readsUtf8BomQuotedCommasAndMultilineContent() {
        String csv = "\uFEFF축제명,축제내용\r\n\"서울, 재즈 축제\",\"첫째 줄\n둘째 줄\"";

        List<List<String>> rows = reader.read(csv.getBytes(StandardCharsets.UTF_8), "UTF-8");

        assertEquals(List.of("축제명", "축제내용"), rows.get(0));
        assertEquals("서울, 재즈 축제", rows.get(1).get(0));
        assertEquals("첫째 줄\n둘째 줄", rows.get(1).get(1));
    }

    @Test
    void rejectsMalformedQuotedFields() {
        byte[] malformed = "축제명\n\"열린 값".getBytes(StandardCharsets.UTF_8);

        assertThrows(InvalidFestivalCsvException.class, () -> reader.read(malformed, "UTF-8"));
    }

    @Test
    void rejectsUnsupportedEncoding() {
        assertThrows(InvalidFestivalCsvException.class,
                () -> reader.read("name".getBytes(StandardCharsets.UTF_8), "UTF-16"));
    }
}
