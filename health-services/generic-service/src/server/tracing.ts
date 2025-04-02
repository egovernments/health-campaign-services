import { initTracer } from 'jaeger-client';
import { FORMAT_HTTP_HEADERS, Tags } from 'opentracing';

// Initialize Jaeger Tracer
const config = {
    serviceName: 'my-express-service',
    sampler: {
        type: 'const',
        param: 1,
    },
    reporter: {
        logSpans: true,
        collectorEndpoint: 'http://localhost:14268/api/traces', // Ensure this URL is correct
    },
};

const options = {
    tags: {
        'my-express-service.version': '1.0.0',
    },
};

const tracer = initTracer(config, options);

export const tracingMiddleware = (req: any, res: any, next: any) => {
    const parentSpanContext = tracer.extract(FORMAT_HTTP_HEADERS, req.headers);

    // Conditionally create span based on parentSpanContext
    const span = parentSpanContext ?
        tracer.startSpan(req.path, { childOf: parentSpanContext }) :
        tracer.startSpan(req.path);

    span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_RPC_SERVER);
    span.setTag(Tags.HTTP_URL, req.url);
    span.setTag(Tags.HTTP_METHOD, req.method);

    req.span = span;

    res.on('finish', () => {
        span.setTag(Tags.HTTP_STATUS_CODE, res.statusCode);
        span.finish();
    });

    next();
};
