/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp;

import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;

public class QosAwareCallFactory {

    private static final int MAX_NUM_REDIRECTS = 20;

    private final UrlSelector urls;
    private final Call.Factory callFactory;
    private volatile int numRedirects = 0;

    public QosAwareCallFactory(UrlSelector urls, Call.Factory callFactory) {
        this.urls = urls;
        this.callFactory = callFactory;
    }

    // TODO(nziebart): visitor should support throwing IOException
    Call nextCall(Call previous, QosException exception) throws IOException {
        if (exception instanceof QosException.Throttle) {
            return nextCall(previous, (QosException.Throttle) exception);
        }
        if (exception instanceof QosException.Unavailable) {
            return nextCall(previous, (QosException.Unavailable) exception);
        }
        if (exception instanceof QosException.RetryOther) {
            return nextCall(previous, (QosException.RetryOther) exception);
        }

        throw new IllegalStateException("unknown exception type: " + exception.getClass());
    }

    private Call nextCall(Call previous, QosException.Throttle exception) throws IOException {
        return previous.clone();
    }

    private Call nextCall(Call previous, QosException.Unavailable exception) throws IOException {
        Request previousRequest = previous.request();
        // TODO(nziebart): why is this method allowed to return empty?
        HttpUrl redirectTo = urls.redirectToNext(previousRequest.url())
                .orElseThrow(() -> new IOException("Failed to determine valid next URL"));

        return redirectedCall(previousRequest, redirectTo);
    }

    private Call nextCall(Call previous, QosException.RetryOther exception) throws IOException {
        Request previousRequest = previous.request();
        if (numRedirects++ > MAX_NUM_REDIRECTS) {
            throw new IOException(
                    "Exceeded the maximum number of allowed redirects for initial URL: " + previousRequest.url());
        }

        HttpUrl redirectTo = urls.redirectTo(previousRequest.url(), exception.getRedirectTo().toString())
                .orElseThrow(() -> new IOException("Failed to determine valid redirect URL for Location "
                        + "header '" + exception.getRedirectTo() + "' and base URLs " + urls.getBaseUrls()));

        return redirectedCall(previousRequest, redirectTo);
    }

    private Call redirectedCall(Request previousRequest, HttpUrl redirectTo) {
        Request newRequest = previousRequest.newBuilder().url(redirectTo).build();
        return callFactory.newCall(newRequest);
    }

}
