package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "fis_mapping_rule_line")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingRuleLine {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "rule_line_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private MappingRule rule;

    @Column(name = "account_code_expression", nullable = false, length = 255)
    private String accountCodeExpression;

    @Column(name = "is_credit", nullable = false)
    private boolean isCredit;

    @Column(name = "amount_expression", nullable = false, length = 255)
    private String amountExpression;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
