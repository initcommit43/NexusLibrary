package dev.nexus.core.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Writes the collected timings onto the response.
 *
 * <p>Hooked here rather than in a filter because a filter only regains control after the
 * body has been written, by which point the headers are already on the wire.
 */
@ControllerAdvice
public class ServerTimingAdvice implements ResponseBodyAdvice<Object> {

    private final ServerTimings timings;

    public ServerTimingAdvice(ServerTimings timings) {
        this.timings = timings;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        timings.header().ifPresent(value -> response.getHeaders().add("Server-Timing", value));
        return body;
    }
}
