package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

public class MappingRuleEvaluationException extends FisBusinessException {

    public MappingRuleEvaluationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "/problems/mapping-rule-evaluation-failed");
    }
}
