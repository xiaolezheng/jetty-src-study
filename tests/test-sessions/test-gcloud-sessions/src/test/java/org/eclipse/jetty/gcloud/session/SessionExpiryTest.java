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


package org.eclipse.jetty.gcloud.session;

import org.eclipse.jetty.server.session.AbstractSessionExpiryTest;
import org.eclipse.jetty.server.session.SessionDataStoreFactory;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;


/**
 * SessionExpiryTest
 *
 *
 */
public class SessionExpiryTest extends AbstractSessionExpiryTest
{

    @After
    public void teardown () throws Exception
    {
       GCloudTestSuite.__testSupport.deleteSessions();
    }
    

    /** 
     * @see org.eclipse.jetty.server.session.AbstractTestBase#createSessionDataStoreFactory()
     */
    @Override
    public SessionDataStoreFactory createSessionDataStoreFactory()
    {
        return GCloudSessionTestSupport.newSessionDataStoreFactory(GCloudTestSuite.__testSupport.getDatastore());
    }



    @Override
    public void verifySessionCreated(TestHttpSessionListener listener, String sessionId)
    {
        super.verifySessionCreated(listener, sessionId);
        try {GCloudTestSuite.__testSupport.assertSessions(1);}catch(Exception e){ Assert.fail(e.getMessage());}
    }




    @Override
    public void verifySessionDestroyed(TestHttpSessionListener listener, String sessionId)
    {
        super.verifySessionDestroyed(listener, sessionId);
        //try{GCloudTestSuite.__testSupport.assertSessions(0);}catch(Exception e) {Assert.fail(e.getMessage());}
    }

    
    
}
