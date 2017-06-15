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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
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
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StacklessLogging;
import org.junit.Before;
import org.junit.Test;



/**
 * CreationTest
 *
 * Test combinations of creating, forwarding and invalidating
 * a session.
 */
public class CreationTest
{

    protected TestServlet _servlet;
    protected TestServer _server1 = null;
    protected CountDownLatch _synchronizer;
    
    @Before
    public void setUp ()
    {
        _synchronizer = new CountDownLatch(1);
        _servlet = new TestServlet(_synchronizer);
    }
    
    
    /**
     * Test creating a session when the cache is set to
     * evict after the request exits.
     * @throws Exception
     */
    @Test
    public void testSessionCreateWithEviction() throws Exception
    {
        String contextPath = "";
        String servletMapping = "/server";
        
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.EVICT_ON_SESSION_EXIT);
        SessionDataStoreFactory storeFactory = new TestSessionDataStoreFactory();
        
        _server1 = new TestServer(0, -1, -1, cacheFactory, storeFactory);
        
        ServletHolder holder = new ServletHolder(_servlet);
        ServletContextHandler contextHandler = _server1.addContext(contextPath);
        contextHandler.addServlet(holder, servletMapping);
        _servlet.setStore(contextHandler.getSessionHandler().getSessionCache().getSessionDataStore());
        _server1.start();
        int port1 = _server1.getPort();

        try (StacklessLogging stackless = new StacklessLogging(Log.getLogger("org.eclipse.jetty.server.session")))
        {
            HttpClient client = new HttpClient();
            client.start();
            String url = "http://localhost:" + port1 + contextPath + servletMapping+"?action=create&check=false";

            //make a request to set up a session on the server
            ContentResponse response = client.GET(url);
            assertEquals(HttpServletResponse.SC_OK,response.getStatus());
            
            String sessionCookie = response.getHeaders().get("Set-Cookie");
            assertTrue(sessionCookie != null);
            // Mangle the cookie, replacing Path with $Path, etc.
            sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");
            
            //session should now be evicted from the cache
            String id = TestServer.extractSessionId(sessionCookie);
            
            assertFalse(contextHandler.getSessionHandler().getSessionCache().contains(id));
            assertTrue(contextHandler.getSessionHandler().getSessionCache().getSessionDataStore().exists(id));
            
            //make another request for the same session
            Request request = client.newRequest("http://localhost:" + port1 + contextPath + servletMapping + "?action=test");
            request.header("Cookie", sessionCookie);
            response = request.send();
            assertEquals(HttpServletResponse.SC_OK,response.getStatus());
            
            //session should now be evicted from the cache again
            assertFalse(contextHandler.getSessionHandler().getSessionCache().contains(TestServer.extractSessionId(sessionCookie)));
           
        }
        finally
        {
            _server1.stop();
        }
    }
    
    
    /**
     * Create and then invalidate a session in the same request.
     * Set SessionCache.setSaveOnCreate(false), so that the creation
     * and immediate invalidation of the session means it is never stored.
     * @throws Exception
     */
    @Test
    public void testSessionCreateAndInvalidateNoSave() throws Exception
    {
        String contextPath = "";
        String servletMapping = "/server";
        int inactivePeriod = 20;
        int scavengePeriod = 3;
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = new TestSessionDataStoreFactory();
        _server1 = new TestServer(0, inactivePeriod, scavengePeriod, cacheFactory, storeFactory);
                
        ServletHolder holder = new ServletHolder(_servlet);
        ServletContextHandler contextHandler = _server1.addContext(contextPath);
        contextHandler.addServlet(holder, servletMapping);
        _servlet.setStore(contextHandler.getSessionHandler().getSessionCache().getSessionDataStore());
        _server1.start();
        int port1 = _server1.getPort();

        try (StacklessLogging stackless = new StacklessLogging(Log.getLogger("org.eclipse.jetty.server.session")))
        {
            HttpClient client = new HttpClient();
            client.start();
            String url = "http://localhost:" + port1 + contextPath + servletMapping+"?action=createinv&check=false";

            //make a request to set up a session on the server
            ContentResponse response = client.GET(url);
            assertEquals(HttpServletResponse.SC_OK,response.getStatus());
            
            //check that the session does not exist
           assertFalse(contextHandler.getSessionHandler().getSessionCache().getSessionDataStore().exists(_servlet._id));
        }
        finally
        {
            _server1.stop();
        }
    }
    
    
    
    
    /**
     * Create and then invalidate a session in the same request.
     * Use SessionCache.setSaveOnCreate(true) and verify the session
     * exists before it is invalidated.
     * @throws Exception
     */
    @Test
    public void testSessionCreateAndInvalidateWithSave() throws Exception
    {
        String contextPath = "";
        String servletMapping = "/server";
        int inactivePeriod = 20;
        int scavengePeriod = 3;
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        cacheFactory.setSaveOnCreate(true);
        SessionDataStoreFactory storeFactory = new TestSessionDataStoreFactory();
        _server1 = new TestServer(0, inactivePeriod, scavengePeriod, cacheFactory, storeFactory);    
        ServletHolder holder = new ServletHolder(_servlet);
        ServletContextHandler contextHandler = _server1.addContext(contextPath);
        contextHandler.addServlet(holder, servletMapping);
        _servlet.setStore(contextHandler.getSessionHandler().getSessionCache().getSessionDataStore());
        _server1.start();
        int port1 = _server1.getPort();

        try (StacklessLogging stackless = new StacklessLogging(Log.getLogger("org.eclipse.jetty.server.session")))
        {
            HttpClient client = new HttpClient();
            client.start();
            String url = "http://localhost:" + port1 + contextPath + servletMapping+"?action=createinv&check=true";

            //make a request to set up a session on the server
            ContentResponse response = client.GET(url);
            assertEquals(HttpServletResponse.SC_OK,response.getStatus());
            
            //check that the session does not exist
           assertFalse(contextHandler.getSessionHandler().getSessionCache().getSessionDataStore().exists(_servlet._id));
        }
        finally
        {
            _server1.stop();
        }
    }
    
    
    
    
    
    
    /**
     * Create a session in a context, forward to another context and create a 
     * session in it too. Check that both sessions exist after the response
     * completes.
     * @throws Exception
     */
    @Test
    public void testSessionCreateForward () throws Exception
    {
        String contextPath = "";
        String contextB = "/contextB";
        String servletMapping = "/server";
        int inactivePeriod = 20;
        int scavengePeriod = 3;
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = new TestSessionDataStoreFactory();
        
        _server1 = new TestServer(0, inactivePeriod, scavengePeriod, cacheFactory, storeFactory);
        
        ServletHolder holder = new ServletHolder(_servlet);
        ServletContextHandler contextHandler = _server1.addContext(contextPath);
        contextHandler.addServlet(holder, servletMapping);
        ServletContextHandler ctxB = _server1.addContext(contextB);
        ctxB.addServlet(TestServletB.class, servletMapping);
        _server1.start();
        int port1 = _server1.getPort();

        try (StacklessLogging stackless = new StacklessLogging(Log.getLogger("org.eclipse.jetty.server.session")))
        {
            HttpClient client = new HttpClient();
            client.start();
            String url = "http://localhost:" + port1 + contextPath + servletMapping;

            //make a request to set up a session on the server
            ContentResponse response = client.GET(url+"?action=forward");
            assertEquals(HttpServletResponse.SC_OK,response.getStatus());
  
            //ensure work has finished on the server side
            _synchronizer.await();
            
            //check that the sessions exist persisted
            assertTrue(contextHandler.getSessionHandler().getSessionCache().getSessionDataStore().exists(_servlet._id));
            assertTrue(ctxB.getSessionHandler().getSessionCache().getSessionDataStore().exists(_servlet._id));
        }
        finally
        {
            _server1.stop();
        }
    }
    
    /**
     * 
     * Create a session in one context, forward to another context and create another session
     * in it, then invalidate the session in the original context: that should invalidate the
     * session in both contexts and no session should exist after the response completes.
     * @throws Exception
     */
    @Test
    public void testSessionCreateForwardAndInvalidate () throws Exception 
    {
        String contextPath = "";
        String contextB = "/contextB";
        String servletMapping = "/server";
        int inactivePeriod = 20;
        int scavengePeriod = 3;
     
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = new TestSessionDataStoreFactory();

        _server1 = new TestServer (0, inactivePeriod, scavengePeriod, cacheFactory, storeFactory);
        
        ServletHolder holder = new ServletHolder(_servlet);
        ServletContextHandler contextHandler = _server1.addContext(contextPath);
        contextHandler.addServlet(holder, servletMapping);
        ServletContextHandler ctxB = _server1.addContext(contextB);
        ctxB.addServlet(TestServletB.class, servletMapping);
        _server1.start();
        int port1 = _server1.getPort();

        try (StacklessLogging stackless = new StacklessLogging(Log.getLogger("org.eclipse.jetty.server.session")))
        {
            HttpClient client = new HttpClient();
            client.start();
            String url = "http://localhost:" + port1 + contextPath + servletMapping;

            //make a request to set up a session on the server
            ContentResponse response = client.GET(url+"?action=forwardinv");
            assertEquals(HttpServletResponse.SC_OK,response.getStatus());
            
            //wait for the request to have finished before checking session
            _synchronizer.await(10, TimeUnit.SECONDS);
            
            //check that the session does not exist 
            assertFalse(contextHandler.getSessionHandler().getSessionCache().getSessionDataStore().exists(_servlet._id));
            assertFalse(ctxB.getSessionHandler().getSessionCache().getSessionDataStore().exists(_servlet._id));      
        }
        finally
        {
            _server1.stop();
        }
    }




    public static class TestServlet extends HttpServlet
    {
        public String _id = null;
        public CountDownLatch _synchronizer;
        public SessionDataStore _store;

        public TestServlet (CountDownLatch latch)
        {
            _synchronizer = latch;
        }

        public void setStore (SessionDataStore store)
        {
            _store = store;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException
        {
            String action = request.getParameter("action");

            if (action != null && action.startsWith("forward"))
            {
                HttpSession session = request.getSession(true);
                _id = session.getId();
                session.setAttribute("value", new Integer(1));

                ServletContext contextB = getServletContext().getContext("/contextB");
                RequestDispatcher dispatcherB = contextB.getRequestDispatcher(request.getServletPath());
                dispatcherB.forward(request, httpServletResponse);

                if (action.endsWith("inv"))
                    session.invalidate();
                else
                {
                    session = request.getSession(false);
                    assertNotNull(session);
                    assertNull(session.getAttribute("B")); //check we don't see stuff from other context
                    
                }
                _synchronizer.countDown();
                return;
            }
            else if (action!=null && "test".equals(action))
            {
                HttpSession session = request.getSession(false);
                assertNotNull(session);
                return;
            }
            else if (action != null && action.startsWith("create"))
            {
                HttpSession session = request.getSession(true);
                _id = session.getId();
                session.setAttribute("value", new Integer(1));

                String check = request.getParameter("check");
                if (!StringUtil.isBlank(check) && _store != null)
                {
                    boolean exists;
                    try
                    {
                        exists = _store.exists(_id);
                    }
                    catch (Exception e)
                    {
                        throw new ServletException (e);
                    }

                    if ("false".equalsIgnoreCase(check))   
                        assertFalse(exists);
                    else
                        assertTrue(exists);
                }

                if ("createinv".equals(action))
                {
                    session.invalidate();
                    assertNull(request.getSession(false));
                    assertNotNull(session);
                }
            }
        }
    }

    public static class TestServletB extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException
        {
            HttpSession session = request.getSession(false);
            assertNull(session);
            if (session == null) session = request.getSession(true);

            // Be sure nothing from contextA is present
            Object objectA = session.getAttribute("value");
            assertTrue(objectA == null);

            // Add something, so in contextA we can check if it is visible (it must not).
            session.setAttribute("B", "B");
        }
    }
}
