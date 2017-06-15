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
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Test;


/**
 * AbstractModifyMaxInactiveIntervalTest
 *
 *
 *
 */
public abstract class AbstractModifyMaxInactiveIntervalTest extends AbstractTestBase
{


    public static int newMaxInactive = 20;
    public static int __scavenge = 1;


    @Test
    public void testReduceMaxInactiveInterval() throws Exception
    {
        int oldMaxInactive = 3;
        int newMaxInactive = 1;
        int sleep = (int)(oldMaxInactive * 0.8);
        
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = createSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(TestServer.DEFAULT_SCAVENGE_SEC);
        
        TestServer server = new TestServer(0, oldMaxInactive,  1, cacheFactory, storeFactory);
        ServletContextHandler ctxA = server.addContext("/mod");
        ctxA.addServlet(TestModServlet.class, "/test");

        server.start();
        int port=server.getPort();
        try
        {
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                // Perform a request to create a session
                ContentResponse response = client.GET("http://localhost:" + port + "/mod/test?action=create");

                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                String sessionCookie = response.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);
                // Mangle the cookie, replacing Path with $Path, etc.
                sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");

                //do another request to reduce the maxinactive interval
                Request request = client.newRequest("http://localhost:" + port + "/mod/test?action=change&val="+newMaxInactive+"&wait="+sleep);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
                //do another request using the cookie to ensure the session is still there
                request= client.newRequest("http://localhost:" + port + "/mod/test?action=test&val="+newMaxInactive);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }
    
    
    @Test
    public void testIncreaseMaxInactiveInterval() throws Exception
    {
        
        int oldMaxInactive = 3;
        int newMaxInactive = 5;
        int sleep = (int)(oldMaxInactive * 0.8);
        
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = createSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(TestServer.DEFAULT_SCAVENGE_SEC);
        
        TestServer server = new TestServer(0, oldMaxInactive,  1, cacheFactory, storeFactory);
        ServletContextHandler ctxA = server.addContext("/mod");
        ctxA.addServlet(TestModServlet.class, "/test");

        server.start();
        int port=server.getPort();
        try
        {
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                // Perform a request to create a session
                ContentResponse response = client.GET("http://localhost:" + port + "/mod/test?action=create");

                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                String sessionCookie = response.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);
                // Mangle the cookie, replacing Path with $Path, etc.
                sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");

                //do another request to increase the maxinactive interval, first waiting until the old expiration should have passed
                Request request = client.newRequest("http://localhost:" + port + "/mod/test?action=change&val="+newMaxInactive+"&wait="+sleep);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
                //do another request using the cookie to ensure the session is still there
                request= client.newRequest("http://localhost:" + port + "/mod/test?action=test&val="+newMaxInactive);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }
    
    
    @Test
    public void testSetMaxInactiveIntervalWithImmortalSessionAndEviction() throws Exception
    {
        int oldMaxInactive = -1;
        int newMaxInactive = 120; //2min
        int evict = 2;
        int sleep = evict;
        
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(evict);
        SessionDataStoreFactory storeFactory = createSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(TestServer.DEFAULT_SCAVENGE_SEC);
        
        TestServer server = new TestServer(0, oldMaxInactive,  1, cacheFactory, storeFactory);
        ServletContextHandler ctxA = server.addContext("/mod");
        ctxA.addServlet(TestModServlet.class, "/test");

        server.start();
        int port=server.getPort();
        try
        {
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                // Perform a request to create a session
                ContentResponse response = client.GET("http://localhost:" + port + "/mod/test?action=create");

                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                String sessionCookie = response.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);
                // Mangle the cookie, replacing Path with $Path, etc.
                sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");

                //do another request to reduce the maxinactive interval
                Request request = client.newRequest("http://localhost:" + port + "/mod/test?action=change&val="+newMaxInactive+"&wait="+sleep);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
                //do another request using the cookie to ensure the session is still there
                request= client.newRequest("http://localhost:" + port + "/mod/test?action=test&val="+newMaxInactive);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }
    
    
    @Test
    public void testSetMaxInactiveIntervalWithNonImmortalSessionAndEviction() throws Exception
    {
        int oldMaxInactive = 10;
        int newMaxInactive = 2;
        int evict = 4;
        int sleep = evict;
        
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(evict);
        SessionDataStoreFactory storeFactory = createSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(TestServer.DEFAULT_SCAVENGE_SEC);
        
        TestServer server = new TestServer(0, oldMaxInactive,  1, cacheFactory, storeFactory);
        ServletContextHandler ctxA = server.addContext("/mod");
        ctxA.addServlet(TestModServlet.class, "/test");

        server.start();
        int port=server.getPort();
        try
        {
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                // Perform a request to create a session
                ContentResponse response = client.GET("http://localhost:" + port + "/mod/test?action=create");

                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                String sessionCookie = response.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);
                // Mangle the cookie, replacing Path with $Path, etc.
                sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");

                //do another request to reduce the maxinactive interval
                Request request = client.newRequest("http://localhost:" + port + "/mod/test?action=change&val="+newMaxInactive+"&wait="+sleep);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
                //do another request using the cookie to ensure the session is still there
                request= client.newRequest("http://localhost:" + port + "/mod/test?action=test&val="+newMaxInactive);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }
    
    @Test
    public void testChangeMaxInactiveIntervalForImmortalSessionNoEviction() throws Exception
    {
        int oldMaxInactive = -1;
        int newMaxInactive = 120;
        
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = createSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(TestServer.DEFAULT_SCAVENGE_SEC);
        
        TestServer server = new TestServer(0, oldMaxInactive,  1, cacheFactory, storeFactory);
        ServletContextHandler ctxA = server.addContext("/mod");
        ctxA.addServlet(TestModServlet.class, "/test");

        server.start();
        int port=server.getPort();
        try
        {
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                // Perform a request to create a session
                ContentResponse response = client.GET("http://localhost:" + port + "/mod/test?action=create");

                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                String sessionCookie = response.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);
                // Mangle the cookie, replacing Path with $Path, etc.
                sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");

                //do another request to change the maxinactive interval
                Request request = client.newRequest("http://localhost:" + port + "/mod/test?action=change&val="+newMaxInactive+"&wait="+2);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
                //do another request using the cookie to ensure the session is still there
                request= client.newRequest("http://localhost:" + port + "/mod/test?action=test&val="+newMaxInactive);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }
    
    @Test
    public void testNoExpireSessionInUse() throws Exception
    {
        int maxInactive = 3;
        int sleep = maxInactive + (int)(maxInactive * 0.8);
        
        
        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = createSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(TestServer.DEFAULT_SCAVENGE_SEC);
        
        TestServer server = new TestServer(0, maxInactive,  1, cacheFactory, storeFactory);
        ServletContextHandler ctxA = server.addContext("/mod");
        ctxA.addServlet(TestModServlet.class, "/test");

        server.start();
        int port=server.getPort();
        try
        {
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                // Perform a request to create a session

                ContentResponse response = client.GET("http://localhost:" + port + "/mod/test?action=create");

                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                String sessionCookie = response.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);
                // Mangle the cookie, replacing Path with $Path, etc.
                sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");

                //do another request that will sleep long enough for the session expiry time to have passed
                //before trying to access the session and ensure it is still there
                Request request = client.newRequest("http://localhost:" + port + "/mod/test?action=sleep&val="+sleep);
                request.header("Cookie", sessionCookie);
                response = request.send();

                assertEquals(HttpServletResponse.SC_OK,response.getStatus());             
                
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }

    @Test
    public void testSessionExpiryAfterModifiedMaxInactiveInterval() throws Exception
    {
        int oldMaxInactive = 4;
        int newMaxInactive = 20;
        int sleep = oldMaxInactive+(int)(oldMaxInactive * 0.8);

        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = createSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(TestServer.DEFAULT_SCAVENGE_SEC);

        TestServer server = new TestServer(0, oldMaxInactive,  __scavenge, cacheFactory, storeFactory);
        ServletContextHandler ctxA = server.addContext("/mod");
        ctxA.addServlet(TestModServlet.class, "/test");

        server.start();
        int port=server.getPort();
        try
        {
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                // Perform a request to create a session
                
                ContentResponse response = client.GET("http://localhost:" + port + "/mod/test?action=create");
                
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                String sessionCookie = response.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);
                // Mangle the cookie, replacing Path with $Path, etc.
                sessionCookie = sessionCookie.replaceFirst("(\\W)(P|p)ath=", "$1\\$Path=");

                //do another request to change the maxinactive interval
                Request request = client.newRequest("http://localhost:" + port + "/mod/test?action=change&val="+newMaxInactive);
                request.header("Cookie", sessionCookie);
                response = request.send();

                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                               
                //wait for longer than the old inactive interval
                Thread.currentThread().sleep(sleep*1000L);
                
                //do another request using the cookie to ensure the session is still there
                request= client.newRequest("http://localhost:" + port + "/mod/test?action=test&val="+newMaxInactive);
                request.header("Cookie", sessionCookie);
                response = request.send();
                assertEquals(HttpServletResponse.SC_OK,response.getStatus());
                
                
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }
    

    public static class TestModServlet extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            String action = request.getParameter("action");
            
            if ("create".equals(action))
            {
                HttpSession session = request.getSession(true);
                return;
            }
            
            if ("change".equals(action))
            {
                //change the expiry time for the session, maybe sleeping before the change
                String tmp = request.getParameter("val");
                int interval = -1;
                interval = (tmp==null?-1:Integer.parseInt(tmp));

                tmp = request.getParameter("wait");
                int wait = (tmp==null?0:Integer.parseInt(tmp));
                if (wait >0)
                {
                    try { Thread.currentThread().sleep(wait*1000);}catch (Exception e) {throw new ServletException(e);}
                }
                HttpSession session = request.getSession(false);
                if (session == null)
                    throw new ServletException("Session is null for action=change");

                if (interval > 0)
                    session.setMaxInactiveInterval(interval);  

                session = request.getSession(false);
                if (session == null)
                    throw new ServletException ("Null session after maxInactiveInterval change");
                return;
            }

            if ("sleep".equals(action))
            {
                //sleep before trying to access the session
              
                HttpSession session = request.getSession(false);
                if (session == null)
                    throw new ServletException("Session is null for action=sleep");

                String tmp = request.getParameter("val");
                int interval = 0;
                interval = (tmp==null?0:Integer.parseInt(tmp));

                if (interval > 0) 
                {
                    try{Thread.currentThread().sleep(interval*1000);}catch (Exception e) {throw new ServletException(e);}
                }

                session = request.getSession(false);
                if (session == null)
                    throw new ServletException("Session null after sleep");

                return;
            }
            
            if ("test".equals(action))
            {
                HttpSession session = request.getSession(false);
                if (session == null)
                    throw new ServletException("Session does not exist");
                String tmp = request.getParameter("val");
                int interval = 0;
                interval = (tmp==null?0:Integer.parseInt(tmp));
                
                assertEquals(interval, session.getMaxInactiveInterval());
                return;
            }
        }
    }
    
}
