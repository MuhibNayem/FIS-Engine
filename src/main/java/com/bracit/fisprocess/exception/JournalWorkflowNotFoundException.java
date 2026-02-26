package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class JournalWorkflowNotFoundException extends FisBusinessException {

    public JournalWorkflowNotFoundException(UUID workflowId) {
        super("Journal workflow '" + workflowId + "' not found.",
                HttpStatus.NOT_FOUND,
                "/problems/journal-workflow-not-found");
    }
}
