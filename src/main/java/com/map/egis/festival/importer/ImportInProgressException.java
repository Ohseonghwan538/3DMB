package com.map.egis.festival.importer;

public class ImportInProgressException extends RuntimeException {
    public ImportInProgressException() {
        super("다른 축제 데이터 적재가 진행 중입니다.");
    }
}
