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

/**
 * The http response of an http request.
 *
 * This class is not thread save in the sense that care must be taken that
 * only one thread does access the write and end methods of it at a time and the
 * completion handler has been called before the next thread access any of this
 * methods. In the sense of visibility it is save to use it by differnt threads.
 *
 * It is very important to allways call end() because otherwise the underlying
 * socket will get blocked.
 *
 * Furthermore, we don't support chunked encoding - hence, the Content-Length
 * must be set correctly _before_ the first data is written!
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public interface HttpServerResponse
{
    /**
     * 100 Continue;
     */
    public static String HTTP_CONTINUE = "100 Continue";

    /**
     * 200 OK
     */
    public static String HTTP_OK = "200 OK";

    /**
     * 201 Created
     */
    public static String HTTP_CREATED = "201 Created";

    /**
     * 204 No Content
     */
    public static String HTTP_NO_CONTENT = "204 No Content";

    /**
     * 301 Moved Permanently
     */
    public static String HTTP_MOVED_PERMANENTLY = "301 Moved Permanently";

    /**
     * 400 Bad Request
     */
    public static String HTTP_BAD_REQUEST = "400 Bad Request";

    /**
     * 403 Forbidden
     */
    public static String HTTP_FORBIDDEN = "403 Forbidden";

    /**
     * 404 File Not Found
     */
    public static String HTTP_NOT_FOUND = "404 File Not Found";

    /**
     * 405 Method Not Allowed
     */
    public static String HTTP_METHOD_NOT_ALLOWED = "405 Method Not Allowed";

    /**
     * 413 Request Entity Too Large
     */
    public static String HTTP_ENTITY_TOO_LARGE = "413 Request Entity Too Large";

    /**
     * 500 Internal Server Error
     */
    public static String HTTP_INTERNAL_ERROR = "500 Internal Server Error";

    /**
     * 503 Service Unavailable
     */
    public static String HTTP_UNAVAILABLE = "503 Service Unavailable";

    /**
     * Set the status of the response.
     *
     * @param status must be the complete status e.g.: "200 OK"
     * @return this response.
     */
    public HttpServerResponse setStatus(String status);

    /**
     * Set any header of the response.
     *
     * we don't support chunked encoding - hence, the Content-Length
     * must be set correctly _before_ the first data is written!
     *
     * @param header the key.
     * @param value the value.
     * @return this response.
     */
    public HttpServerResponse setHeader(String header, String value);

    /**
     * Asynchronously write the body of this request.
     *
     * This method is not thread save in the sense that care must be taken that
     * only one thread does access it at a time and the completion handler has been
     * called before the next thread access this method. In the sense of visibility
     * it is save to use it by differnt threads.
     *
     * we don't support chunked encoding - hence, the Content-Length
     * must be set correctly _before_ the first data is written!
     *
     * @param <A> the type of the attachment.
     * @param buffer the buffer to write.
     * @param attachment the attachment to pass along.
     * @param handler the handler to call back if something has been written.
     * @return this response.
     */
    public <A> HttpServerResponse write(ByteBuffer buffer, A attachment, CompletionHandler<Void,? super A> handler);

    /**
     * Asynchronously end this request.
     *
     * This method is not thread save in the sense that care must be taken that
     * only one thread does access it at a time together with the write() method
     * and the completion handler of the latter has been
     * called before the next thread access this method. In the sense of visibility
     * it is save to use it by differnt threads.
     *
     * we don't support chunked encoding - hence, the Content-Length
     * must be set correctly _before_ the first data is written!
     *
     * @return this response which shouldn't be used from now on.
     */
    public HttpServerResponse end();
}
