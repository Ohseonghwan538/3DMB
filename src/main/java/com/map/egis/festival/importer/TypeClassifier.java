package com.map.egis.festival.importer;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TypeClassifier {
    private final List<TypeRule> rules = List.of(
            new TypeRule("MUSIC", List.of("음악", "뮤직", "콘서트", "재즈", "록", "락페스티벌", "밴드")),
            new TypeRule("TRADITION", List.of("전통", "역사", "문화재", "민속", "한옥", "무형문화")),
            new TypeRule("FOOD", List.of("음식", "먹거리", "미식", "요리", "맛집", "푸드")),
            new TypeRule("NATURE", List.of("꽃", "국화", "벚꽃", "철쭉", "단풍", "자연", "생태", "유채")),
            new TypeRule("ART", List.of("예술", "공연", "미술", "연극", "무용", "전시", "영화")));

    public String classify(String name, String content) {
        String searchable = ((name == null ? "" : name) + " " + (content == null ? "" : content))
                .toLowerCase(java.util.Locale.ROOT);
        return rules.stream()
                .filter(rule -> rule.keywords().stream().anyMatch(searchable::contains))
                .map(TypeRule::code)
                .findFirst()
                .orElse("ETC");
    }

    private record TypeRule(String code, List<String> keywords) { }
}
