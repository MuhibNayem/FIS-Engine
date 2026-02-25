package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a journal entry is not found.
 */
public class JournalEntryNotFoundException extends FisBusinessException {

    public JournalEntryNotFoundException(UUID journalEntryId) {
        super(
                String.format("Journal entry '%s' not found", journalEntryId),
                HttpStatus.NOT_FOUND,
                "/problems/journal-entry-not-found");
    }
}
