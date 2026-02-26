package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class MappingRuleNotFoundException extends FisBusinessException {

    public MappingRuleNotFoundException(UUID ruleId) {
        super("Mapping rule '" + ruleId + "' was not found.", HttpStatus.NOT_FOUND, "/problems/mapping-rule-not-found");
    }
}
