//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.server;

import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.junit.After;
import org.junit.Test;

public class InsufficientThreadsDetectionTest {

    private Server _server;

    @After
    public void dispose() throws Exception
    {
        _server.stop();
    }

    @Test(expected = IllegalStateException.class)
    public void testConnectorUsesServerExecutorWithNotEnoughThreads() throws Exception
    {
        // server has 3 threads in the executor
        _server = new Server(new QueuedThreadPool(3));

        // connector will use executor from server because connectorPool is null
        ThreadPool connectorPool = null;
        // connector requires 7 threads(2 + 4 + 1)
        ServerConnector connector = new ServerConnector(_server, connectorPool, null, null, 2, 4, new HttpConnectionFactory());
        connector.setPort(0);
        _server.addConnector(connector);

        // should throw IllegalStateException because there are no required threads in server pool
        _server.start();
    }

    @Test
    public void testConnectorWithDedicatedExecutor() throws Exception
    {
        // server has 3 threads in the executor
        _server = new Server(new QueuedThreadPool(3));

        // connector pool has 100 threads
        ThreadPool connectorPool = new QueuedThreadPool(100);
        // connector requires 7 threads(2 + 4 + 1)
        ServerConnector connector = new ServerConnector(_server, connectorPool, null, null, 2, 4, new HttpConnectionFactory());
        connector.setPort(0);
        _server.addConnector(connector);

        // should not throw exception because connector uses own executor, so its threads should not be counted
        _server.start();
    }

    @Test // Github issue #586
    public void testCaseForMultipleConnectors() throws Exception {
        // server has 4 threads in the executor
        _server = new Server(new QueuedThreadPool(4));

        // first connector consumes all 4 threads from server pool
        _server.addConnector(new ServerConnector(_server, null, null, null, 1, 1, new HttpConnectionFactory()));

        // second connect also require 4 threads but uses own executor, so its threads should not be counted
        final QueuedThreadPool connectorPool = new QueuedThreadPool(4, 4);
        _server.addConnector(new ServerConnector(_server, connectorPool, null, null, 1, 1, new HttpConnectionFactory()));

        // should not throw exception because limit was not overflown
        _server.start();
    }

}
