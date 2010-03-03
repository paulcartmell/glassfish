/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.web;

import com.sun.enterprise.config.serverbeans.SessionProperties;
import com.sun.enterprise.deployment.runtime.web.ManagerProperties;
import com.sun.enterprise.deployment.runtime.web.SessionManager;
import com.sun.enterprise.deployment.runtime.web.StoreProperties;
import com.sun.enterprise.deployment.runtime.web.WebProperty;
import com.sun.logging.LogDomains;
import org.apache.catalina.Context;
import org.apache.catalina.core.StandardContext;
import org.jvnet.hk2.config.types.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BasePersistenceStrategyBuilder
        implements PersistenceStrategyBuilder {

    protected static final Logger _logger = LogDomains.getLogger(
            BasePersistenceStrategyBuilder.class, LogDomains.WEB_LOGGER);
    protected static final ResourceBundle _rb = _logger.getResourceBundle();

    protected String directory = null;
    protected String DEFAULT_SESSION_FILENAME = "SESSIONS.ser";
    protected String sessionFilename = DEFAULT_SESSION_FILENAME;
    // START CR 6275709
    protected String sessionIdGeneratorClassname = null;
    // END CR 6275709
    protected String _persistenceFrequency = null;
    protected String _persistenceScope = null;
    protected String _passedInPersistenceType = null;
    protected int maxSessions = -1;
    protected final int DEFAULT_REAP_INTERVAL = 60;   // 1 minute
    protected int reapInterval = DEFAULT_REAP_INTERVAL;
    protected int storeReapInterval = DEFAULT_REAP_INTERVAL;
    protected final int DEFAULT_MAX_IDLE_BACKUP = -1;   // never save
    protected int maxIdleBackup = DEFAULT_MAX_IDLE_BACKUP;
    protected final int DEFAULT_SESSION_TIMEOUT = 1800;   // 30 minute
    protected int sessionMaxInactiveInterval = DEFAULT_SESSION_TIMEOUT;

    // Special constant for Java Server Faces
    protected static final String JSF_HA_ENABLED = "com.sun.appserver.enableHighAvailability";    

    public void initializePersistenceStrategy(
            Context ctx,
            SessionManager smBean,
            ServerConfigLookup serverConfigLookup) {

        /*
         * This method sets default values.
         * It may be extended in builder subclasses which will have their
         * own inst vars for additional params.
         */
        setDefaultParams(ctx, smBean);

        /*
         * This method reads server instance-level parameter values from
         * domain.xml.
         * Any values found here will over-ride defaults.
         * This method may be extended in builder subclasses which will have
         * their own inst vars for additional params.
         */
        readInstanceLevelParams(serverConfigLookup);
        
        /*
         * This method reads web app parameter values from sun-web.xml.
         * Any values found here will over-ride defaults & instance-level
         * values.
         * This method may be extended in builder subclasses which will have
         * their own inst vars for additional params.
         */
        readWebAppParams(ctx, smBean);        

        ctx.setBackgroundProcessorDelay(reapInterval);

        // For intra-vm session locking
        StandardContext sctx = (StandardContext) ctx;
        sctx.restrictedSetPipeline(new PESessionLockingStandardPipeline(sctx));

    }
    
    public void setDefaultParams(Context ctx, SessionManager smBean) {
        
        reapInterval = DEFAULT_REAP_INTERVAL;

        maxSessions = -1;

        // Default settings for persistence-type = 'memory'
        sessionFilename = DEFAULT_SESSION_FILENAME;

        // Default settings for persistence-type = 'file'
        storeReapInterval = DEFAULT_REAP_INTERVAL;

        directory = ((StandardContext) ctx).getWorkDir(); 
    }
    
    
    public void readInstanceLevelParams(ServerConfigLookup serverConfigLookup) {

        com.sun.enterprise.config.serverbeans.SessionManager smBean =
            serverConfigLookup.getInstanceSessionManager();
     
        if (smBean != null) {
            // The persistence-type controls what properties of the 
            // session manager can be configured
            
            com.sun.enterprise.config.serverbeans.ManagerProperties mgrBean =
                smBean.getManagerProperties();
            if (mgrBean != null) {
                // manager reap-interval-in-seconds
                String reapIntervalInSecondsString = mgrBean.getReapIntervalInSeconds();
                if (reapIntervalInSecondsString != null) {
                    try {
                        reapInterval = Integer.parseInt(reapIntervalInSecondsString);
                        if (_logger.isLoggable(Level.FINEST)) {
                            _logger.finest("mgr reapInterval set = " +
                                           reapInterval);
                        }
                    } catch (NumberFormatException e) {
                        // XXX need error message
                    }                        
                } else {
                    if (_logger.isLoggable(Level.FINEST)) {
                        _logger.finest("no instance level value set for mgr reapInterval");
                    }
                }                               
                //max-sessions
                String maxSessionsString = mgrBean.getMaxSessions();
                if (maxSessionsString != null) {
                    try {
                        maxSessions = Integer.parseInt(maxSessionsString);
                        if (_logger.isLoggable(Level.FINEST)) {
                            _logger.finest("maxSessions set = " + maxSessions);
                        }
                    } catch (NumberFormatException e) {
                        // XXX need error message
                    }                        
                } else {
                    if (_logger.isLoggable(Level.FINEST)) {
                        _logger.finest("no instance level value set for maxSessions");
                    }
                } 

                //session-file-name
                String sessionFilenameString = mgrBean.getSessionFileName();
                if (sessionFilenameString != null) {
                    sessionFilename = sessionFilenameString;
                    if (_logger.isLoggable(Level.FINEST)) {
                        _logger.finest("sessionFilename set = " +
                                       sessionFilename);
                    }
                }

                // START CR 6275709
                sessionIdGeneratorClassname =
                    mgrBean.getSessionIdGeneratorClassname();
                if (sessionIdGeneratorClassname != null
                        && _logger.isLoggable(Level.FINEST)) {
                    _logger.finest("sessionIdGeneratorClassname set = " +
                                   sessionIdGeneratorClassname);
                }
                // END CR 6275709
                
                // Now do properties under <manager-properties> element
                List<Property> props = mgrBean.getProperty();
                if (props != null) {
                    for (Property prop : props) {
                        String name = prop.getName();
                        String value = prop.getValue();
                        // maxIdleBackupSeconds
                        if (name.equalsIgnoreCase("maxIdleBackupSeconds")) {
                            try {
                                maxIdleBackup = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                // XXX need error message
                            }
                        }
                    }
                }
            }            
            
            com.sun.enterprise.config.serverbeans.StoreProperties storeBean =
                smBean.getStoreProperties();
            
            if (storeBean != null) {
                // Store reap-interval-in-seconds
                String reapIntervalInSecondsString = storeBean.getReapIntervalInSeconds();
                if (reapIntervalInSecondsString != null) {
                    try {
                        storeReapInterval = Integer.parseInt(reapIntervalInSecondsString);
                        _logger.finest("storeReapInterval set = " +
                                       storeReapInterval);
                    } catch (NumberFormatException e) {
                        // XXX need error message
                    }
                }
                // Directory
                String directoryString = storeBean.getDirectory();
                if (directoryString != null) {
                    directory = directoryString;
                    _logger.finest("directory set = " + directoryString);
                }                                                     
            }                     
        }
      
        SessionProperties spBean =
            serverConfigLookup.getInstanceSessionProperties();
        if (spBean != null) {
            // session timeout-in-seconds
            String timeoutSecondsString = spBean.getTimeoutInSeconds();
            if (timeoutSecondsString != null) {
                try {
                    sessionMaxInactiveInterval = Integer.parseInt(timeoutSecondsString);
                    _logger.finest("sessionMaxInactiveInterval set = " +
                                   sessionMaxInactiveInterval);
                } catch (NumberFormatException e) {
                    // XXX need error message
                }                        
            } else {
                if (_logger.isLoggable(Level.FINEST)) {
                    _logger.finest("no instance level value set for sessionMaxInactiveInterval");
                }                
            }            
        }
    }
    
    public void readWebAppParams(Context ctx, SessionManager smBean ) {    
    
        if (smBean != null) {
            // The persistence-type controls what properties of the 
            // session manager can be configured            
            ManagerProperties mgrBean = smBean.getManagerProperties();
            if ((mgrBean != null) && (mgrBean.sizeWebProperty() > 0)) {
                WebProperty[] props = mgrBean.getWebProperty();
                for (int i = 0; i < props.length; i++) {                    
                    String name = props[i].getAttributeValue(WebProperty.NAME);
                    String value = props[i].getAttributeValue(WebProperty.VALUE);                    
                    if (name.equalsIgnoreCase("reapIntervalSeconds")) {
                        try {
                           reapInterval = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // XXX need error message
                        }
                    } else if (name.equalsIgnoreCase("maxSessions")) {
                        try {
                            maxSessions = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // XXX need error message
                        }
                    } else if (name.equalsIgnoreCase("maxIdleBackupSeconds")) {
                        try {
                            maxIdleBackup = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // XXX need error message
                        }                        
                    } else if (name.equalsIgnoreCase("sessionFilename")) {
                        sessionFilename = value;                        
                    } else if (name.equalsIgnoreCase("persistenceFrequency")) {
                        _persistenceFrequency = value;
                    } else {
                        Object[] params = { name };
                        _logger.log(Level.INFO, "webcontainer.notYet", params);
                    }
                }
            }

            StoreProperties storeBean = smBean.getStoreProperties();
            if ((storeBean != null) && (storeBean.sizeWebProperty() > 0)) {
                WebProperty[] props = storeBean.getWebProperty();
                for (int i = 0; i < props.length; i++) {
                    String name = props[i].getAttributeValue(WebProperty.NAME);
                    String value = props[i].getAttributeValue(WebProperty.VALUE);                    
                    if (name.equalsIgnoreCase("reapIntervalSeconds")) {
                        try {
                            storeReapInterval = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // XXX need error message
                        }
                    } else if (name.equalsIgnoreCase("directory")) {
                        directory = value;
                    } else if (name.equalsIgnoreCase("persistenceScope")) {
                        _persistenceScope = value;                        
                    } else {
                        Object[] params = { name };
                        _logger.log(Level.INFO, "webcontainer.notYet", params);
                    }
                }
            }
        }
    }
    
    protected String prependContextPathTo(String str, Context ctx) {
        if (str == null) {
            return str;
        }
        String filePart = getFilePartOf(str);
        if (filePart == null || filePart.equals("")) {
            return null;
        }
        String strippedContextPath = stripNonAlphaNumericsExceptUnderscore(ctx.getPath());
        String modifiedFilePart = null;
        if (strippedContextPath != null && !strippedContextPath.equals("")) {
            modifiedFilePart = strippedContextPath + "_" + filePart;
        } else {
            modifiedFilePart = filePart;
        }
        int lastSlashIdx = str.lastIndexOf(File.separator);
        String result = null;
        if (lastSlashIdx == -1) {
            result = modifiedFilePart;
        } else {
            String firstPart = str.substring(0, lastSlashIdx);
            result = firstPart + File.separator + modifiedFilePart;
        }
        return result;
    }
    
    protected String getFilePartOf(String str) {
        if (str == null) {
            return str;
        }
        int lastSlashIdx = str.lastIndexOf(File.separator);
        String result = null;
        if (lastSlashIdx == -1) {
            result = cleanFileParts(str);
        } else {
            result = cleanFileParts(str.substring(lastSlashIdx + 1, str.length()));
        }
        return result;
    }
    
    private String cleanFileParts(String fileString) {
        String fileMainPart = getFileMainPart(fileString);
        String fileSuffixPart = getFileSuffixPart(fileString);
        if (fileMainPart == null) {
            return null;
        }
        if (fileSuffixPart == null) {
            return fileMainPart;
        } else {
            return fileMainPart + "." + fileSuffixPart;
        }
    }
    
    private String getFileMainPart(String fileString) {
        ArrayList results = new ArrayList();
        StringTokenizer st = new StringTokenizer(fileString, ".");
        while (st.hasMoreTokens()) {
            results.add(st.nextToken());
        }
        if (results.size() > 0) {
            return stripNonAlphaNumericsExceptUnderscore((String)results.get(0));
        } else {
            return null;
        }
    }
    
    private String getFileSuffixPart(String fileString) {
        ArrayList results = new ArrayList();
        StringTokenizer st = new StringTokenizer(fileString, ".");
        while (st.hasMoreTokens()) {
            results.add(st.nextToken());
        }
        if (results.size() > 1) {
            return stripNonAlphaNumericsExceptUnderscore((String)results.get(1));
        } else {
            return null;
        }
    }    
    
    /**
     * this method strips out all non-alpha characters
     *
     * @param inputString
     */     
    protected String stripNonAlphas(String inputString) {
        StringBuilder sb = new StringBuilder(50);
        for (int i=0; i<inputString.length(); i++) {
            char nextChar = inputString.charAt(i);
            if (Character.isLetter(nextChar)) {
                sb.append(nextChar);
            }
        }
        return sb.toString();
    } 
    
    /**
     * this method strips out all non-alphanumeric characters
     *
     * @param inputString
     */     
    protected String stripNonAlphaNumericsExceptUnderscore(String inputString) {
        StringBuilder sb = new StringBuilder(50);
        for (int i=0; i<inputString.length(); i++) {
            char nextChar = inputString.charAt(i);
            if (Character.isLetterOrDigit(nextChar) || "_".equals(String.valueOf(nextChar))) {
                sb.append(nextChar);
            }
        }
        return sb.toString();
    }
    
    /**
     * this method strips out all non-alphanumeric characters
     *
     * @param inputString
     */     
    protected String stripNonAlphaNumerics(String inputString) {
        StringBuilder sb = new StringBuilder(50);
        for (int i=0; i<inputString.length(); i++) {
            char nextChar = inputString.charAt(i);
            if (Character.isLetterOrDigit(nextChar)) {
                sb.append(nextChar);
            }
        }
        return sb.toString();
    }     
    
    public String getPersistenceFrequency() {
        return _persistenceFrequency;
    }
    
    public void setPersistenceFrequency(String persistenceFrequency) {
        _persistenceFrequency = persistenceFrequency;
    }
    
    public String getPersistenceScope() {
        return _persistenceScope;
    }
    
    public void setPersistenceScope(String persistenceScope) {
        _persistenceScope = persistenceScope;
    }
    
    public String getPassedInPersistenceType() {
        return _passedInPersistenceType;
    }    
    
    public void setPassedInPersistenceType(String persistenceType) {
        _passedInPersistenceType = persistenceType;
    }    
}
