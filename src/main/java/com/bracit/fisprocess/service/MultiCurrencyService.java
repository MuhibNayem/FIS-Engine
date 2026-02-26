package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;

public interface MultiCurrencyService {
    DraftJournalEntry apply(DraftJournalEntry draft);
}
