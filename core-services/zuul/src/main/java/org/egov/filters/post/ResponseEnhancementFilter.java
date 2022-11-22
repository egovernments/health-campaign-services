package org.egov.filters.post;

import com.google.common.io.CharStreams;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPOutputStream;

import static org.egov.constants.RequestContextConstants.CORRELATION_ID_KEY;

/**
 * Sets the correlation id to the response header.
 */
@Slf4j
@Component
public class ResponseEnhancementFilter extends ZuulFilter {

    private static final String CORRELATION_HEADER_NAME = "x-correlation-id";
    private static final String RECEIVED_RESPONSE_MESSAGE = "Received response code: {} from upstream URI {}";
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public static String compress(String str) throws IOException {
        if (str == null || str.length() == 0) {
            return str;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(str.getBytes());
        gzip.close();
        return out.toString("UTF-8");
    }

    @Override
    public String filterType() {
        return "post";
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();

        String resBody = readResponseBody(ctx);
        try {
            log.info("resBody {}", resBody);
        } catch (Exception err) {
            log.error(err.toString());
        }
        ctx.addZuulResponseHeader(CORRELATION_HEADER_NAME, getCorrelationId());
        ctx.addZuulResponseHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        return null;
    }

    private String getCorrelationId() {
        RequestContext ctx = RequestContext.getCurrentContext();
        logger.info(RECEIVED_RESPONSE_MESSAGE,
            ctx.getResponse().getStatus(), ctx.getRequest().getRequestURI());
        return (String) ctx.get(CORRELATION_ID_KEY);
    }

    private String readResponseBody(RequestContext ctx) {
        String responseBody = null;
        try (final InputStream responseDataStream = ctx.getResponseDataStream()) {
            responseBody = CharStreams.toString(new InputStreamReader(responseDataStream, "UTF-8"));
            responseBody = compress(responseBody);
            ctx.setResponseBody(responseBody);
        } catch (IOException e) {
            log.error("Error reading body", e);
        } catch (Exception e) {
            log.error(e.toString());
            log.error("Exception while reading response body: " + e.getMessage());
        }
        return responseBody;
    }
}
