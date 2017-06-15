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

package org.eclipse.jetty.security;

import org.eclipse.jetty.util.PathWatcher;
import org.eclipse.jetty.util.PathWatcher.PathWatchEvent;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Credential;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * PropertyUserStore
 * <p>
 * This class monitors a property file of the format mentioned below and notifies registered listeners of the changes to the the given file.
 *
 * <pre>
 *  username: password [,rolename ...]
 * </pre>
 *
 * Passwords may be clear text, obfuscated or checksummed. The class com.eclipse.Util.Password should be used to generate obfuscated passwords or password
 * checksums.
 *
 * If DIGEST Authentication is used, the password must be in a recoverable format, either plain text or OBF:.
 */
public class PropertyUserStore extends UserStore implements PathWatcher.Listener
{
    private static final Logger LOG = Log.getLogger(PropertyUserStore.class);

    private static final String JAR_FILE = "jar:file:";

    protected Path _configPath;
    protected Resource _configResource;
    
    protected PathWatcher pathWatcher;
    protected boolean hotReload = false; // default is not to reload

    protected boolean _firstLoad = true; // true if first load, false from that point on
    protected List<UserListener> _listeners;

    /**
     * Get the config (as a string)
     * @return the config path as a string
     * @deprecated use {@link #getConfigPath()} instead
     */
    @Deprecated
    public String getConfig()
    {
        if (_configPath != null)
            return _configPath.toString();
        return null;
    }

    /**
     * Set the Config Path from a String reference to a file
     * @param config the config file
     */
    public void setConfig(String config)
    {
        try
        {
            Resource configResource = Resource.newResource(config);
            if (configResource.getFile() != null)
                setConfigPath(configResource.getFile());
            else
                throw new IllegalArgumentException(config+" is not a file");
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

    }
    
    /**
     * Get the Config {@link Path} reference.
     * @return the config path
     */
    public Path getConfigPath()
    {
        return _configPath;
    }

    /**
     * Set the Config Path from a String reference to a file
     * @param configFile the config file can a be a file path or a reference to a file within a jar file <code>jar:file:</code>
     */
    public void setConfigPath(String configFile)
    {
        if (configFile == null)
        {
            _configPath = null;
        }
        else if (new File( configFile ).exists())
        {
            _configPath = new File(configFile).toPath();
        }
        if ( !new File( configFile ).exists() && configFile.startsWith( JAR_FILE ))
        {
            // format of the url is jar:file:/foo/bar/beer.jar!/mountain_goat/pale_ale.txt
            // ideally we'd like to extract this to Resource class?
            try
            {
                _configPath = extractPackedFile( configFile );
            }
            catch ( IOException e )
            {
                throw new RuntimeException( "cannot extract file from url:" + configFile, e );
            }
        }
    }

    private Path extractPackedFile( String configFile )
        throws IOException
    {
        int fileIndex = configFile.indexOf( "!" );
        String entryPath = configFile.substring( fileIndex + 1, configFile.length() );

        Path tmpDirectory = Files.createTempDirectory( "users_store" );
        Path extractedPath = Paths.get(tmpDirectory.toString(), entryPath);
        // delete if exists as copyTo do not overwrite
        Files.deleteIfExists( extractedPath );
        // delete on shutdown
        extractedPath.toFile().deleteOnExit();
        JarResource.newResource( configFile ).copyTo( tmpDirectory.toFile() );
        return extractedPath;
    }

    /**
     * Set the Config Path from a {@link File} reference
     * @param configFile the config file
     */
    public void setConfigPath(File configFile)
    {
        if(configFile == null)
        {
            _configPath = null;
            return;
        }
        
        _configPath = configFile.toPath();
    }

    /**
     * Set the Config Path
     * @param configPath the config path
     */
    public void setConfigPath(Path configPath)
    {
        _configPath = configPath;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the resource associated with the configured properties file, creating it if necessary
     * @throws IOException if unable to get the resource
     */
    public Resource getConfigResource() throws IOException
    {
        if (_configResource == null)
        {
            _configResource = new PathResource(_configPath);
        }

        return _configResource;
    }
    
    /**
     * Is hot reload enabled on this user store
     * 
     * @return true if hot reload was enabled before startup
     */
    public boolean isHotReload()
    {
        return hotReload;
    }

    /**
     * Enable Hot Reload of the Property File
     * 
     * @param enable true to enable, false to disable
     */
    public void setHotReload(boolean enable)
    {
        if (isRunning())
        {
            throw new IllegalStateException("Cannot set hot reload while user store is running");
        }
        this.hotReload = enable;
    }

   
    
    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        s.append(this.getClass().getName());
        s.append("[");
        s.append("users.count=").append(this.getKnownUserIdentities().size());
        s.append("identityService=").append(this.getIdentityService());
        s.append("]");
        return s.toString();
    }

    /* ------------------------------------------------------------ */
    protected void loadUsers() throws IOException
    {
        if (_configPath == null)
            return;

        if (LOG.isDebugEnabled())
        {
            LOG.debug("Loading " + this + " from " + _configPath);
        }
        
        Properties properties = new Properties();
        if (getConfigResource().exists())
            properties.load(getConfigResource().getInputStream());
        
        Set<String> known = new HashSet<>();

        for (Map.Entry<Object, Object> entry : properties.entrySet())
        {
            String username = ((String)entry.getKey()).trim();
            String credentials = ((String)entry.getValue()).trim();
            String roles = null;
            int c = credentials.indexOf(',');
            if (c > 0)
            {
                roles = credentials.substring(c + 1).trim();
                credentials = credentials.substring(0,c).trim();
            }

            if (username != null && username.length() > 0 && credentials != null && credentials.length() > 0)
            {
                String[] roleArray = IdentityService.NO_ROLES;
                if (roles != null && roles.length() > 0)
                {
                    roleArray = StringUtil.csvSplit(roles);
                }
                known.add(username);
                Credential credential = Credential.getCredential(credentials);
                addUser( username, credential, roleArray );
                notifyUpdate(username,credential,roleArray);
            }
        }

        final List<String> currentlyKnownUsers = new ArrayList<String>(getKnownUserIdentities().keySet());
        /*
         * 
         * if its not the initial load then we want to process removed users
         */
        if (!_firstLoad)
        {
            Iterator<String> users = currentlyKnownUsers.iterator();
            while (users.hasNext())
            {
                String user = users.next();
                if (!known.contains(user))
                {
                    removeUser( user );
                    notifyRemove(user);
                }
            }
        }


        /*
         * set initial load to false as there should be no more initial loads
         */
        _firstLoad = false;

        if (LOG.isDebugEnabled())
        {
            LOG.debug("Loaded " + this + " from " + _configPath);
        }
    }
    
    /* ------------------------------------------------------------ */
    /**
     * Depending on the value of the refresh interval, this method will either start up a scanner thread that will monitor the properties file for changes after
     * it has initially loaded it. Otherwise the users will be loaded and there will be no active monitoring thread so changes will not be detected.
     *
     *
     * @see org.eclipse.jetty.util.component.AbstractLifeCycle#doStart()
     */
    protected void doStart() throws Exception
    {
        super.doStart();

        loadUsers();
        if ( isHotReload() && (_configPath != null) )
        {
            this.pathWatcher = new PathWatcher();
            this.pathWatcher.watch(_configPath);
            this.pathWatcher.addListener(this);
            this.pathWatcher.setNotifyExistingOnStart(false);
            this.pathWatcher.start();
        }
       
    }
    
    @Override
    public void onPathWatchEvent(PathWatchEvent event)
    {
        try
        {
            if (LOG.isDebugEnabled())
            {
                LOG.debug( "PATH WATCH EVENT: {}", event.getType() );
            }
            loadUsers();
        }
        catch (IOException e)
        {
            LOG.warn(e);
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.util.component.AbstractLifeCycle#doStop()
     */
    protected void doStop() throws Exception
    {
        super.doStop();
        if (this.pathWatcher != null)
            this.pathWatcher.stop();
        this.pathWatcher = null;
    }

    /**
     * Notifies the registered listeners of potential updates to a user
     *
     * @param username
     * @param credential
     * @param roleArray
     */
    private void notifyUpdate(String username, Credential credential, String[] roleArray)
    {
        if (_listeners != null)
        {
            for (Iterator<UserListener> i = _listeners.iterator(); i.hasNext();)
            {
                i.next().update(username,credential,roleArray);
            }
        }
    }

    /**
     * notifies the registered listeners that a user has been removed.
     *
     * @param username
     */
    private void notifyRemove(String username)
    {
        if (_listeners != null)
        {
            for (Iterator<UserListener> i = _listeners.iterator(); i.hasNext();)
            {
                i.next().remove(username);
            }
        }
    }

    /**
     * registers a listener to be notified of the contents of the property file
     * @param listener the user listener
     */
    public void registerUserListener(UserListener listener)
    {
        if (_listeners == null)
        {
            _listeners = new ArrayList<UserListener>();
        }
        _listeners.add(listener);
    }

    /**
     * UserListener
     */
    public interface UserListener
    {
        public void update(String username, Credential credential, String[] roleArray);

        public void remove(String username);
    }
}
