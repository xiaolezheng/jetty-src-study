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

package org.eclipse.jetty.server.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Before;
import org.junit.Test;


/**
 * SameContextForwardedSessionTest
 *
 * Test that creating a session inside a forward on the same context works, and that
 * attributes set after the forward returns are preserved.
 */
public class SameContextForwardedSessionTest
{
    protected CountDownLatch _synchronizer;
    protected Servlet1 _one;
    
    @Before
    public void setUp ()
    {
        _synchronizer = new CountDownLatch(1);
        _one = new Servlet1();
        _one.setSynchronizer(_synchronizer);
    }
    
    
    @Test
    public void testSessionCreateInForward() throws Exception
    {
        
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = new TestSessionDataStoreFactory();
        TestServer testServer = new TestServer(0, -1,  -1, cacheFactory, storeFactory);

        ServletContextHandler testServletContextHandler = testServer.addContext("/context");
        ServletHolder holder = new ServletHolder(_one);
        testServletContextHandler.addServlet(holder, "/one");
        testServletContextHandler.addServlet(Servlet2.class, "/two");
        testServletContextHandler.addServlet(Servlet3.class, "/three");
        testServletContextHandler.addServlet(Servlet4.class, "/four");
       
      

        try
        {
            testServer.start();
            int serverPort=testServer.getPort();
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                //make a request to the first servlet, which will forward it to other servlets
                ContentResponse response = client.GET("http://localhost:" + serverPort + "/context/one");
                assertEquals(HttpServletResponse.SC_OK, response.getStatus());
                String sessionCookie = response.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);
                // Mangle the cookie, replacing Path with $Path, etc.
                sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");

                //wait until all of the request handling has finished 
                _synchronizer.await();
                
                //test that the session was created, and that it contains the attributes from servlet3 and servlet1
                testServletContextHandler.getSessionHandler().getSessionCache().contains(TestServer.extractSessionId(sessionCookie));
                testServletContextHandler.getSessionHandler().getSessionCache().getSessionDataStore().exists(TestServer.extractSessionId(sessionCookie));
       
                //Make a fresh request
                Request request = client.newRequest("http://localhost:" + serverPort + "/context/four");
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK, response.getStatus());
                
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            testServer.stop();
        }
        
    }
    

    public static class Servlet1 extends HttpServlet
    {
        CountDownLatch _synchronizer;
        
        public void setSynchronizer(CountDownLatch sync)
        {
            _synchronizer = sync;
        }
        
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            //Don't create a session, just forward to another session in the same context
            assertNull(request.getSession(false));
            
            //The session will be created by the other servlet, so will exist as this dispatch returns
            RequestDispatcher dispatcher = request.getServletContext().getRequestDispatcher("/two");
            dispatcher.forward(request, response);
   
            HttpSession sess = request.getSession(false);
            assertNotNull(sess);
            assertNotNull(sess.getAttribute("servlet3"));
            sess.setAttribute("servlet1", "servlet1");
            
            if (_synchronizer != null)
                _synchronizer.countDown();
        }
    }

    public static class Servlet2 extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            //forward to yet another servlet to do the creation
            assertNull(request.getSession(false));

            RequestDispatcher dispatcher = request.getServletContext().getRequestDispatcher("/three");
            dispatcher.forward(request, response);
            
            //the session should exist after the forward
            HttpSession sess = request.getSession(false);
            assertNotNull(sess);
            assertNotNull(sess.getAttribute("servlet3"));
        }
    }



    public static class Servlet3 extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {    
            //No session yet
            assertNull(request.getSession(false));
            
            //Create it
            HttpSession session = request.getSession();
            assertNotNull(session);
            
            //Set an attribute on it
            session.setAttribute("servlet3", "servlet3");
        }
    }
    
    
    public static class Servlet4 extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {    
            //Check that the session contains attributes set during and after the session forward
            HttpSession session = request.getSession();
            assertNotNull(session);
            assertNotNull(session.getAttribute("servlet1"));
            assertNotNull(session.getAttribute("servlet3"));
        }
    }
    
}
