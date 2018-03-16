/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package feign;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingInputStream;
import com.palantir.remoting3.jaxrs.JaxRsClient;
import com.palantir.remoting3.jaxrs.TestBase;
import com.palantir.remoting3.jaxrs.feignimpl.GuavaTestServer;
import feign.codec.Decoder;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

public final class InputStreamDelegateDecoderTest extends TestBase {
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(GuavaTestServer.class,
            "src/test/resources/test-server.yml");

    private GuavaTestServer.TestService service;
    private Decoder delegate;
    private Decoder inputStreamDelegateDecoder;

    @Before
    public void before() {
        delegate = Mockito.mock(Decoder.class);
        inputStreamDelegateDecoder = new InputStreamDelegateDecoder(delegate);

        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.create(GuavaTestServer.TestService.class, AGENT, createTestConfig(endpointUri));
    }

    @Test
    public void testDecodesAsInputStream() throws Exception {
        String data = "data";

        Response response = Response.create(200, "OK", ImmutableMap.of(), data, StandardCharsets.UTF_8);

        InputStream decoded = (InputStream) inputStreamDelegateDecoder.decode(response, InputStream.class);

        assertThat(new String(Util.toByteArray(decoded), StandardCharsets.UTF_8), is(data));
    }

    @Test
    public void testUsesDelegateWhenReturnTypeNotInputStream() throws Exception {
        String returned = "string";

        when(delegate.decode(any(), any())).thenReturn(returned);
        Response response = Response.create(200, "OK", ImmutableMap.of(), returned, StandardCharsets.UTF_8);
        String decodedObject = (String) inputStreamDelegateDecoder.decode(response, String.class);
        assertEquals(returned, decodedObject);
    }

    @Test
    public void testStandardClientsUseInputStreamDelegateDecoder() throws IOException {
        String data = "bytes";
        assertThat(Util.toByteArray(service.writeInputStream(data)), is(bytes(data)));
    }

    @Test
    public void testInputStreamIsNotBuffered() throws IOException {
        // Creating an array of size Integer.MAX_VALUE will always throw an OutOfMemoryError
        try (InputStream response = service.writeInputStream(Integer.MAX_VALUE);
             CountingInputStream countingInputStream = new CountingInputStream(response)) {
            ByteStreams.copy(countingInputStream, ByteStreams.nullOutputStream());
            assertThat(countingInputStream.getCount(), is((long) Integer.MAX_VALUE));
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
