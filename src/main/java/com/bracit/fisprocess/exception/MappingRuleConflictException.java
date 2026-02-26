package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class MappingRuleConflictException extends FisBusinessException {

    public MappingRuleConflictException(String eventType) {
        super("Mapping rule for eventType '" + eventType + "' already exists.", HttpStatus.CONFLICT,
                "/problems/mapping-rule-conflict");
    }
}
