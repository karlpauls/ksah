/*
 * Copyright 2016 Karl Pauls (karlpauls@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.kalpatec.ksah;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.Map;

/**
 * The request object of a http request.
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public interface HttpServerRequest
{
    /**
     * Get the request headers.
     *
     * @return the headers of the request.
     */
    public Map<String,String> headers();

    /**
     * Returns the value of the specified header, if present.
     *
     * its case insensitve.
     *
     * @param header The header value to retrieve.
     * @return The value of the specified header or <tt>null</tt>.
    **/
    public String getHeader(String header);

    /**
     * Get the request method.
     *
     * @return the method of the request.
     */
    public String method();

    /**
     * Get the requested uri.
     *
     * @return the uri of the request.
     */
    public String uri();

    /**
     * Get the http version of the request
     *
     * @return the version.
     */
    public String version();

    /**
     * Get the decoded path of the request.
     *
     * @return the decoded path of the request.
     */
    public String path();

    /**
     * Asynchronously read the body of the request (if any).
     *
     * This method is not thread save in the sense that care must be taken that
     * only one thread does access it at a time and the completion handler has been
     * called before the next thread access this method. In the sense of visibility
     * it is save to use it by differnt threads.
     *
     * @param <A> the type of the attachment.
     * @param buffer the buffer to read into.
     * @param attachment the attachment to pass along.
     * @param handler the handler to call back if something has been read.
     */
    public <A> void read(ByteBuffer buffer, A attachment, CompletionHandler<Integer, ? super A> handler);
}
