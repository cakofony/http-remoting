/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.tracing.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.remoting3.tracing.Tracer;
import java.io.ByteArrayOutputStream;
import javax.ws.rs.core.StreamingOutput;
import org.junit.Test;

public final class JaxRsTracersTest {

    @Test
    public void testWrappingStreamingOutput_streamingOutputTraceIsIsolated() throws Exception {
        Tracer.startSpan("outside");
        StreamingOutput streamingOutput = JaxRsTracers.wrap((os) -> {
            Tracer.startSpan("inside"); // never completed
        });
        streamingOutput.write(new ByteArrayOutputStream());
        assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("outside");
    }

    @Test
    public void testWrappingStreamingOutput_traceStateIsCapturedAtConstructionTime() throws Exception {
        Tracer.startSpan("before-construction");
        StreamingOutput streamingOutput = JaxRsTracers.wrap((os) -> {
            assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("before-construction");
        });
        Tracer.startSpan("after-construction");
        streamingOutput.write(new ByteArrayOutputStream());
    }
}
