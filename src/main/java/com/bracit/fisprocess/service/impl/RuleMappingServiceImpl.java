package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.MappingRule;
import com.bracit.fisprocess.domain.entity.MappingRuleLine;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.exception.MappingRuleEvaluationException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.MappingRuleRepository;
import com.bracit.fisprocess.service.RuleMappingService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleMappingServiceImpl implements RuleMappingService {

    private final MappingRuleRepository mappingRuleRepository;
    private final BusinessEntityRepository businessEntityRepository;

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public DraftJournalEntry mapToDraft(UUID tenantId, FinancialEventRequestDto event, @Nullable String fallbackCreatedBy) {
        BusinessEntity tenant = businessEntityRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        List<DraftJournalLine> lines = toLinesFromEventOrRule(tenantId, event);

        String createdBy = event.getCreatedBy() != null && !event.getCreatedBy().isBlank()
                ? event.getCreatedBy()
                : (fallbackCreatedBy == null ? "system" : fallbackCreatedBy);

        return DraftJournalEntry.builder()
                .tenantId(tenantId)
                .eventId(event.getEventId())
                .postedDate(event.getPostedDate())
                .description(event.getDescription())
                .referenceId(event.getReferenceId())
                .transactionCurrency(event.getTransactionCurrency())
                .baseCurrency(tenant.getBaseCurrency())
                .createdBy(createdBy)
                .lines(lines)
                .build();
    }

    private List<DraftJournalLine> toLinesFromEventOrRule(UUID tenantId, FinancialEventRequestDto event) {
        if (event.getLines() != null && !event.getLines().isEmpty()) {
            return event.getLines().stream().map(this::mapRequestLine).toList();
        }

        MappingRule rule = mappingRuleRepository.findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, event.getEventType())
                .orElseThrow(() -> new MappingRuleEvaluationException(
                        "No active mapping rule found for eventType '" + event.getEventType() + "'."));

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("event", event);
        context.put("payload", event.getPayload() == null ? Map.of() : event.getPayload());

        return rule.getLines().stream()
                .sorted(Comparator.comparingInt(MappingRuleLine::getSortOrder))
                .map(line -> DraftJournalLine.builder()
                        .accountCode(evalString(line.getAccountCodeExpression(), context))
                        .amountCents(evalLong(line.getAmountExpression(), context))
                        .baseAmountCents(evalLong(line.getAmountExpression(), context))
                        .isCredit(line.isCredit())
                        .build())
                .toList();
    }

    private DraftJournalLine mapRequestLine(JournalLineRequestDto line) {
        return DraftJournalLine.builder()
                .accountCode(line.getAccountCode())
                .amountCents(line.getAmountCents())
                .baseAmountCents(line.getAmountCents())
                .isCredit(line.isCredit())
                .dimensions(line.getDimensions())
                .build();
    }

    private String evalString(String expression, Map<String, Object> contextMap) {
        if (!isTemplate(expression)) {
            return expression;
        }
        String normalized = normalizeTemplate(expression);
        Object value = evaluateExpression(normalized, contextMap);
        if (value == null) {
            throw new MappingRuleEvaluationException("Expression resolved to null: " + expression);
        }
        return String.valueOf(value);
    }

    private long evalLong(String expression, Map<String, Object> contextMap) {
        String normalized = normalizeTemplate(expression);
        Object value;
        try {
            value = evaluateExpression(normalized, contextMap);
        } catch (RuntimeException ex) {
            if (!isTemplate(expression)) {
                return Long.parseLong(expression);
            }
            throw new MappingRuleEvaluationException("Failed to evaluate amount expression: " + expression);
        }

        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String asString) {
            return Long.parseLong(asString);
        }
        throw new MappingRuleEvaluationException("Amount expression must resolve to numeric value: " + expression);
    }

    private boolean isTemplate(String expression) {
        return expression.startsWith("${") && expression.endsWith("}");
    }

    private String normalizeTemplate(String expression) {
        if (isTemplate(expression)) {
            return expression.substring(2, expression.length() - 1).trim();
        }
        if (expression.startsWith("#{") && expression.endsWith("}")) {
            return expression.substring(2, expression.length() - 1).trim();
        }
        return expression;
    }

    private Object evaluateExpression(String normalizedExpression, Map<String, Object> contextMap) {
        try {
            return parser.parseExpression(normalizedExpression)
                    .getValue(SimpleEvaluationContext.forReadOnlyDataBinding().build(), contextMap);
        } catch (RuntimeException ex) {
            if (normalizedExpression.startsWith("payload.")) {
                String key = normalizedExpression.substring("payload.".length());
                Object payloadRaw = contextMap.get("payload");
                if (payloadRaw instanceof Map<?, ?> payloadMap) {
                    return payloadMap.get(key);
                }
            }
            throw ex;
        }
    }
}
