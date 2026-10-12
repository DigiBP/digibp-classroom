package ch.fhnw.digibp.classroom.config;

import org.cibseven.webapp.rest.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(assignableTypes = TaskService.class)
public class DeployedFormResponseAdvice implements ResponseBodyAdvice<byte[]> {

    private static final Logger log =
            LoggerFactory.getLogger(DeployedFormResponseAdvice.class);

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {

        return returnType.getMethod() != null
                && "getDeployedForm".equals(returnType.getMethod().getName());
    }

    @Override
    public byte[] beforeBodyWrite(
            byte[] body,
            MethodParameter returnType,
            MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        response.getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);

        log.debug("Removed Transfer-Encoding header from deployed form response");

        return body;
    }
}
