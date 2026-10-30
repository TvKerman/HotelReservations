package com.booking.gateway.filter;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "traceId";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String traceId = exchange.getRequest().getHeaders().getFirst(HEADER);
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    final String finalTraceId = traceId;
    MDC.put(MDC_KEY, finalTraceId);

    ServerWebExchange mutated =
        exchange.mutate().request(r -> r.header(HEADER, finalTraceId)).build();

    mutated.getResponse().getHeaders().add(HEADER, finalTraceId);

    return chain.filter(mutated).doFinally(signal -> MDC.remove(MDC_KEY));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
