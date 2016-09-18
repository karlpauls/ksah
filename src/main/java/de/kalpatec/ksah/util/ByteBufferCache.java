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
package de.kalpatec.ksah.util;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A ByteBuffer cache.
 *
 * The general problem with native ByteBuffer is that while they do get gc'ed they
 * are not counted by the heap memory so they will not trigger a gc - hence, this
 * buffer can be used to limit the native memory used to a given amount and start
 * using heap memory ByteBuffer if the limit is hit.
 *
 * Native (i.e., direct) ByteBuffers are created and cached up to the limit.
 * 
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public class ByteBufferCache
{
    private final ConcurrentLinkedQueue<ByteBuffer> m_queue = new ConcurrentLinkedQueue<>();
    private final int m_bufferSize;

    private ByteBufferCache(int bufferSize)
    {
        m_bufferSize = bufferSize;
    }

    public static ByteBufferCache create(int bufferCount, int bufferSize)
    {
        ByteBufferCache cache = new ByteBufferCache(bufferSize);

        for (int i = 0; i < bufferCount; i++)
        {
            cache.m_queue.add(ByteBuffer.allocateDirect(bufferSize));
        }

        return cache;
    }

    public ByteBuffer checkout()
    {
        ByteBuffer result = m_queue.poll();

        if (result == null)
        {
            result = ByteBuffer.allocate(m_bufferSize);
        }

        return result;
    }

    public void checkin(ByteBuffer buffer)
    {
        if (buffer.isDirect())
        {
            buffer.clear();
            m_queue.add(buffer);
        }
    }
}
