package com.bracit.fisprocess.domain;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.AuditLog;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.ExchangeRate;
import com.bracit.fisprocess.domain.entity.IdempotencyLog;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.JournalLine;
import com.bracit.fisprocess.domain.entity.MappingRule;
import com.bracit.fisprocess.domain.entity.MappingRuleLine;
import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.domain.entity.PeriodRevaluationRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entity Lombok Policy Tests")
class EntityLombokPolicyTest {

    private static final List<Class<?>> ENTITY_CLASSES = List.of(
            Account.class,
            AccountingPeriod.class,
            AuditLog.class,
            BusinessEntity.class,
            ExchangeRate.class,
            IdempotencyLog.class,
            JournalEntry.class,
            JournalLine.class,
            MappingRule.class,
            MappingRuleLine.class,
            OutboxEvent.class,
            PeriodRevaluationRun.class);

    private String readSource(Class<?> entityClass) {
        try {
            Path sourcePath = Path.of(System.getProperty("user.dir"), "src", "main", "java",
                    entityClass.getName().replace('.', '/') + ".java");
            return Files.readString(sourcePath);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read source for " + entityClass.getName(), ex);
        }
    }

    @Test
    @DisplayName("entities should not use Lombok @Data")
    void entitiesShouldNotUseDataAnnotation() {
        for (Class<?> entityClass : ENTITY_CLASSES) {
            assertThat(readSource(entityClass))
                    .as("Entity %s must not use @Data", entityClass.getSimpleName())
                    .doesNotContain("@Data");
        }
    }

    @Test
    @DisplayName("entities should declare explicit equals/hashCode policy")
    void entitiesShouldDeclareEqualsAndHashCodePolicy() {
        for (Class<?> entityClass : ENTITY_CLASSES) {
            assertThat(readSource(entityClass))
                    .as("Entity %s must declare @EqualsAndHashCode", entityClass.getSimpleName())
                    .contains("@EqualsAndHashCode");
        }
    }
}
