package com.github.redxiiikk.spring.cloud.gateway.loadbalancer.isolation

import com.github.redxiiikk.spring.cloud.gateway.loadbalancer.isSingle
import com.github.redxiiikk.spring.cloud.gateway.loadbalancer.isolation.generator.LoadBalancerIsolationHeaderValueGenerator
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono


class LoadBalancerIsolationHeaderGeneratorFilter(
    private val property: LoadbalancerIsolationConfigProperty,
    private val generator: LoadBalancerIsolationHeaderValueGenerator
) : GlobalFilter {
    companion object {
        private val logger = LoggerFactory.getLogger(LoadBalancerIsolationHeaderGeneratorFilter::class.java)
    }

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        return chain.filter(doIsolationHeaderValueGenerate(exchange))
    }

    private fun doIsolationHeaderValueGenerate(exchange: ServerWebExchange): ServerWebExchange {
        val request = exchange.request.mutate()
            .headers { headers ->
                val isolationHeaderValue = generator.generateHeaderValue(exchange)
                if (headers.needAddIsolationHeader(isolationHeaderValue)) {
                    logger.debug("the value generated by our policy should be added into the header.")
                    headers.add(property.isolationHeaderKey, isolationHeaderValue)
                }
            }
            .build()
        return exchange.mutate().request(request).build()
    }

    private fun HttpHeaders.needAddIsolationHeader(isolationHeaderValue: String): Boolean {
        if (this.containsKey(property.isolationHeaderKey)) {
            logger.debug("this request already has isolation header.")

            val headerValues = this[property.isolationHeaderKey]!!

            if (headerValues.isSingle()) {
                if (headerValues.single() == isolationHeaderValue) {
                    logger.debug("isolation header value is same as generated by our policy in this request.")
                }
            } else {
                logger.warn("header container too many value in this request.")

                if (headerValues.contains(isolationHeaderValue)) {
                    logger.debug("this header value contains that value generated by our policy in this request.")
                }
            }

            return false
        }
        return true
    }
}