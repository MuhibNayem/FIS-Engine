package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.MappingRule;
import com.bracit.fisprocess.domain.entity.MappingRuleLine;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.exception.MappingRuleEvaluationException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.MappingRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleMappingServiceImpl Unit Tests")
class RuleMappingServiceImplTest {

    @Mock
    private MappingRuleRepository mappingRuleRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;

    private RuleMappingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleMappingServiceImpl(mappingRuleRepository, businessEntityRepository);
    }

    @Test
    @DisplayName("mapToDraft should use explicit event lines when provided")
    void mapToDraftShouldUseEventLines() {
        UUID tenantId = UUID.randomUUID();
        when(businessEntityRepository.findById(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                .eventId("EVT-LINES")
                .eventType("SALE")
                .postedDate(LocalDate.of(2026, 2, 1))
                .transactionCurrency("USD")
                .createdBy("alice")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH").amountCents(100L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REV").amountCents(100L).isCredit(true).build()))
                .build();

        var draft = service.mapToDraft(tenantId, event, "fallback");

        assertThat(draft.getCreatedBy()).isEqualTo("alice");
        assertThat(draft.getLines()).hasSize(2);
        assertThat(draft.getLines().get(0).getBaseAmountCents()).isEqualTo(100L);
    }

    @Test
    @DisplayName("mapToDraft should evaluate SpEL rule lines from payload")
    void mapToDraftShouldEvaluateRuleLines() {
        UUID tenantId = UUID.randomUUID();
        when(businessEntityRepository.findById(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        MappingRule rule = MappingRule.builder()
                .tenantId(tenantId)
                .eventType("SALE")
                .isActive(true)
                .lines(List.of(
                        MappingRuleLine.builder()
                                .sortOrder(1)
                                .accountCodeExpression("${payload.debitAccount}")
                                .amountExpression("${payload.amountCents}")
                                .isCredit(false)
                                .build()))
                .build();
        when(mappingRuleRepository.findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, "SALE"))
                .thenReturn(Optional.of(rule));

        FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                .eventId("EVT-RULE")
                .eventType("SALE")
                .postedDate(LocalDate.of(2026, 2, 1))
                .transactionCurrency("USD")
                .payload(Map.of("debitAccount", "CASH", "amountCents", 500L))
                .build();

        var draft = service.mapToDraft(tenantId, event, "system");

        assertThat(draft.getLines()).hasSize(1);
        assertThat(draft.getLines().get(0).getAccountCode()).isEqualTo("CASH");
        assertThat(draft.getLines().get(0).getAmountCents()).isEqualTo(500L);
    }

    @Test
    @DisplayName("mapToDraft should fail when expression resolves to non-numeric amount")
    void mapToDraftShouldFailForInvalidAmountExpression() {
        UUID tenantId = UUID.randomUUID();
        when(businessEntityRepository.findById(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        MappingRule rule = MappingRule.builder()
                .tenantId(tenantId)
                .eventType("SALE")
                .isActive(true)
                .lines(List.of(
                        MappingRuleLine.builder()
                                .sortOrder(1)
                                .accountCodeExpression("${payload.debitAccount}")
                                .amountExpression("${payload.badAmount}")
                                .isCredit(false)
                                .build()))
                .build();
        when(mappingRuleRepository.findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, "SALE"))
                .thenReturn(Optional.of(rule));

        FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                .eventId("EVT-BAD")
                .eventType("SALE")
                .postedDate(LocalDate.of(2026, 2, 1))
                .transactionCurrency("USD")
                .payload(Map.of("debitAccount", "CASH", "badAmount", Map.of("x", 1)))
                .build();

        assertThatThrownBy(() -> service.mapToDraft(tenantId, event, "system"))
                .isInstanceOf(MappingRuleEvaluationException.class);
    }

    @Test
    @DisplayName("mapToDraft should fail when tenant not found")
    void mapToDraftShouldFailWhenTenantMissing() {
        UUID tenantId = UUID.randomUUID();
        when(businessEntityRepository.findById(tenantId)).thenReturn(Optional.empty());

        FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                .eventId("EVT")
                .eventType("SALE")
                .postedDate(LocalDate.now())
                .transactionCurrency("USD")
                .build();

        assertThatThrownBy(() -> service.mapToDraft(tenantId, event, "system"))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    @DisplayName("mapToDraft should reuse compiled expression from cache")
    void mapToDraftShouldReuseCompiledExpression() {
        UUID tenantId = UUID.randomUUID();
        when(businessEntityRepository.findById(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        MappingRule rule = MappingRule.builder()
                .tenantId(tenantId)
                .eventType("SALE")
                .isActive(true)
                .lines(List.of(MappingRuleLine.builder()
                        .sortOrder(1)
                        .accountCodeExpression("CASH")
                        .amountExpression("${payload.amountCents}")
                        .isCredit(false)
                        .build()))
                .build();
        when(mappingRuleRepository.findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, "SALE"))
                .thenReturn(Optional.of(rule));

        ExpressionParser parser = mock(ExpressionParser.class);
        Expression expression = mock(Expression.class);
        when(parser.parseExpression("payload.amountCents")).thenReturn(expression);
        when(expression.getValue(any(), any(Map.class))).thenAnswer(invocation -> {
            Map<String, Object> context = invocation.getArgument(1);
            Map<?, ?> payload = (Map<?, ?>) context.get("payload");
            return payload.get("amountCents");
        });

        RuleMappingServiceImpl cachedService = new RuleMappingServiceImpl(
                mappingRuleRepository, businessEntityRepository);
        cachedService.configureExpressionCacheForTesting(parser, 8);

        FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                .eventId("EVT-CACHE")
                .eventType("SALE")
                .postedDate(LocalDate.of(2026, 2, 1))
                .transactionCurrency("USD")
                .payload(Map.of("amountCents", 250L))
                .build();

        cachedService.mapToDraft(tenantId, event, "system");
        cachedService.mapToDraft(tenantId, event, "system");

        verify(parser, times(1)).parseExpression("payload.amountCents");
    }

    @Test
    @DisplayName("mapToDraft should evict eldest compiled expression when cache is full")
    void mapToDraftShouldEvictEldestCompiledExpressionWhenCacheFull() {
        UUID tenantId = UUID.randomUUID();
        when(businessEntityRepository.findById(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));

        when(mappingRuleRepository.findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, "SALE-1"))
                .thenReturn(Optional.of(singleLineRule(tenantId, "SALE-1", "${payload.amount1}")));
        when(mappingRuleRepository.findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, "SALE-2"))
                .thenReturn(Optional.of(singleLineRule(tenantId, "SALE-2", "${payload.amount2}")));
        when(mappingRuleRepository.findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, "SALE-3"))
                .thenReturn(Optional.of(singleLineRule(tenantId, "SALE-3", "${payload.amount3}")));

        ExpressionParser parser = mock(ExpressionParser.class);
        when(parser.parseExpression(anyString())).thenAnswer(invocation -> {
            String expressionText = invocation.getArgument(0);
            Expression expression = mock(Expression.class);
            when(expression.getValue(any(), any(Map.class))).thenAnswer(valueInvocation -> {
                Map<String, Object> context = valueInvocation.getArgument(1);
                Map<?, ?> payload = (Map<?, ?>) context.get("payload");
                String payloadKey = expressionText.startsWith("payload.")
                        ? expressionText.substring("payload.".length())
                        : expressionText;
                return payload.get(payloadKey);
            });
            return expression;
        });

        RuleMappingServiceImpl cachedService = new RuleMappingServiceImpl(
                mappingRuleRepository, businessEntityRepository);
        cachedService.configureExpressionCacheForTesting(parser, 2);

        cachedService.mapToDraft(tenantId, eventFor("SALE-1", "amount1", 100L), "system");
        cachedService.mapToDraft(tenantId, eventFor("SALE-2", "amount2", 200L), "system");
        cachedService.mapToDraft(tenantId, eventFor("SALE-3", "amount3", 300L), "system");
        cachedService.mapToDraft(tenantId, eventFor("SALE-1", "amount1", 400L), "system");

        verify(parser, times(2)).parseExpression("payload.amount1");
        verify(parser, times(1)).parseExpression("payload.amount2");
        verify(parser, times(1)).parseExpression("payload.amount3");
    }

    @Test
    @DisplayName("expression cache should reduce parse operations for repeated workloads")
    void expressionCacheShouldReduceParseOperationsForRepeatedWorkloads() {
        UUID tenantId = UUID.randomUUID();
        when(businessEntityRepository.findById(tenantId))
                .thenReturn(Optional.of(BusinessEntity.builder().tenantId(tenantId).baseCurrency("USD").build()));
        when(mappingRuleRepository.findByTenantIdAndEventTypeAndIsActiveTrue(tenantId, "SALE"))
                .thenReturn(Optional.of(singleLineRule(tenantId, "SALE", "${payload.amountCents}")));

        ExpressionParser cachedParser = mock(ExpressionParser.class);
        Expression cachedExpression = mock(Expression.class);
        when(cachedParser.parseExpression("payload.amountCents")).thenReturn(cachedExpression);
        when(cachedExpression.getValue(any(), any(Map.class))).thenAnswer(invocation -> {
            Map<String, Object> context = invocation.getArgument(1);
            Map<?, ?> payload = (Map<?, ?>) context.get("payload");
            return payload.get("amountCents");
        });

        RuleMappingServiceImpl cachedService = new RuleMappingServiceImpl(
                mappingRuleRepository, businessEntityRepository);
        cachedService.configureExpressionCacheForTesting(cachedParser, 8);

        for (int i = 0; i < 50; i++) {
            cachedService.mapToDraft(tenantId, eventFor("SALE", "amountCents", 100L), "system");
        }

        verify(cachedParser, times(1)).parseExpression("payload.amountCents");
    }

    private MappingRule singleLineRule(UUID tenantId, String eventType, String amountExpression) {
        return MappingRule.builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .isActive(true)
                .lines(List.of(MappingRuleLine.builder()
                        .sortOrder(1)
                        .accountCodeExpression("CASH")
                        .amountExpression(amountExpression)
                        .isCredit(false)
                        .build()))
                .build();
    }

    private FinancialEventRequestDto eventFor(String eventType, String amountKey, long amount) {
        return FinancialEventRequestDto.builder()
                .eventId("EVT-" + eventType)
                .eventType(eventType)
                .postedDate(LocalDate.of(2026, 2, 1))
                .transactionCurrency("USD")
                .payload(Map.of(amountKey, amount))
                .build();
    }
}
