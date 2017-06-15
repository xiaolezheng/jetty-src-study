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
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jetty.util.IO;

/**
 * FileTestHelper
 * 
 */
public class FileTestHelper
{
    static int __workers=0;
    static File _tmpDir;
    
    public  static void setup ()
    throws Exception
    {
        
        _tmpDir = File.createTempFile("file", null);
        _tmpDir.delete();
        _tmpDir.mkdirs();
        _tmpDir.deleteOnExit();
    }
    
    
    public static void teardown ()
    {
        IO.delete(_tmpDir);
        _tmpDir = null;
    }
    
    
    public static void assertStoreDirEmpty (boolean isEmpty)
    {
        assertNotNull(_tmpDir);
        assertTrue(_tmpDir.exists());
        String[] files = _tmpDir.list();
        if (isEmpty)
        {
            if (files != null)
                assertEquals(0, files.length);
        }
        else
        {
            assertNotNull(files);
            assertFalse(files.length==0);
        }
    }

    
    public static File getFile (String sessionId)
    {
        assertNotNull(_tmpDir);
        assertTrue(_tmpDir.exists());
        String[] files = _tmpDir.list();
        assertNotNull(files);
        String fname = null;
        for (String name:files)
        {
            if (name.contains(sessionId))
            {
                fname=name;
                break;
            }
        }
        if (fname != null)
            return new File (_tmpDir, fname);
        return null;
    }

    public static void assertFileExists (String sessionId, boolean exists)
    {
        assertNotNull(_tmpDir);
        assertTrue(_tmpDir.exists());
        String[] files = _tmpDir.list();
        assertNotNull(files);
        if (exists)
            assertFalse(files.length == 0);
        boolean found = false;
        for (String name:files)
        {
            if (name.contains(sessionId))
            {
                found = true;
                break;
            }
        }
        if (exists)
            assertTrue(found);
        else
            assertFalse(found);
    }
    
    
    public static void deleteFile (String sessionId)
    {
        assertNotNull(_tmpDir);
        assertTrue(_tmpDir.exists());
        String[] files = _tmpDir.list();
        assertNotNull(files);
        assertFalse(files.length == 0);
        String filename = null;
        for (String name:files)
        {
            if (name.contains(sessionId))
            {
                filename = name;
                break;
            }
        }
        if (filename != null)
        {
            File f = new File (_tmpDir, filename);
            assertTrue(f.delete());
        }
    }
 
    public static FileSessionDataStoreFactory newSessionDataStoreFactory()
    {
        FileSessionDataStoreFactory storeFactory = new FileSessionDataStoreFactory();
        storeFactory.setStoreDir(_tmpDir);
        return storeFactory;
    }
}
