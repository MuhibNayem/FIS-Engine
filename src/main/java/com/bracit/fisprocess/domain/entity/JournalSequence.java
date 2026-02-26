package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tenant/year-specific sequence allocator for immutable posted journal entries.
 */
@Entity
@Table(name = "fis_journal_sequence")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class JournalSequence {

    @EqualsAndHashCode.Include
    @EmbeddedId
    private JournalSequenceId id;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;
}
