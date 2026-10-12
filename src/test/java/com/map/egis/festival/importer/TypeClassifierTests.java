package com.map.egis.festival.importer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeClassifierTests {
    private final TypeClassifier classifier = new TypeClassifier();

    @Test
    void appliesKeywordPriorityAndFallsBackToEtc() {
        assertEquals("MUSIC", classifier.classify("재즈 음악 축제", "벚꽃과 함께하는 공연"));
        assertEquals("NATURE", classifier.classify("벚꽃 축제", null));
        assertEquals("ETC", classifier.classify("우리 동네 축제", "주민 화합 행사"));
    }
}
