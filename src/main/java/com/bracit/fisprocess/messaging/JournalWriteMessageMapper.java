package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class JournalWriteMessageMapper {

    private final ModelMapper modelMapper;

    public JournalWriteMessageMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public DraftJournalEntry toDraft(CreateJournalEntryRequestDto request) {
        return modelMapper.map(request, DraftJournalEntry.class);
    }
}