/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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

package org.glassfish.osgicdi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

import javax.enterprise.inject.spi.InjectionPoint;

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A simple Service Factory class that provides the ability to obtain/get 
 * references to a service implementation (obtained from a service registry) 
 * and also provides a mechanism to unget or return a service after its usage
 * is completed.
 * 
 * @author Sivakumar Thyagarajan
 */
class OSGiServiceFactory {
    private static final boolean DEBUG_ENABLED = true;

    /**
     * Get a reference to the service of the provided <code>Type</code>
     * @throws ServiceUnavailableException 
     */
    public static Object getService(final InjectionPoint svcInjectionPoint) 
                                    throws ServiceUnavailableException{
        final OSGiService os = svcInjectionPoint.getAnnotated().getAnnotation(OSGiService.class);
        debug("getService " + svcInjectionPoint.getType() + " OS:" + os);
        Object instance = createServiceProxy(svcInjectionPoint); 
        return instance;
    }

    private static Object createServiceProxy(
            final InjectionPoint svcInjectionPoint) throws ServiceUnavailableException {
        Type serviceType = svcInjectionPoint.getType();
        final OSGiService os = svcInjectionPoint.getAnnotated().getAnnotation(OSGiService.class);

        //Get one service instance when the proxy is created
        final Object svcInstance = lookupService(svcInjectionPoint);
        InvocationHandler proxyInvHndlr = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                Object instanceToUse = svcInstance;
                if (os.dynamic()) {
                    //If the service is marked as dynamic, when a method is invoked on a 
                    //a service proxy, an attempt is made to get a reference to the service 
                    //and then the method is invoked on the newly obtained service.
                    //This scheme should work for statless and/or idempotent service 
                    //implementations that have a dynamic lifecycle that is not linked to
                    //the service consumer [service dynamism]
                    //TODO: we should track the lookedup service and 
                    //only if it goes away should we look up a service
                    debug ("looking a service as this is set to DYNAMIC=true");
                    instanceToUse =  lookupService(svcInjectionPoint);
                } else {
                    debug ("using the service that was looked up earlier" +
                    		" as this is set to DYNAMIC=false");
                }
                debug("calling Method " + method + " on proxy");
                return method.invoke(instanceToUse, args);
            }
        };
        
        Object instance =  Proxy.newProxyInstance(
                            Thread.currentThread().getContextClassLoader(), 
                            new Class[]{(Class)serviceType}, 
                            proxyInvHndlr);
        return instance;
    }

    private static Object lookupService(InjectionPoint svcInjectionPoint) 
                        throws ServiceUnavailableException {
        Type serviceType = svcInjectionPoint.getType();
        final OSGiService os = svcInjectionPoint.getAnnotated().getAnnotation(OSGiService.class);
        debug("lookup service" + serviceType);
        
        //Get the bundle context from the classloader that loaded the annotation
        //element
        Class annotatedElt = svcInjectionPoint.getMember().getDeclaringClass();
        BundleContext bc = BundleReference.class
                            .cast(annotatedElt.getClassLoader())
                            .getBundle().getBundleContext();
        
        //Create the service tracker for this type.
        debug("creating service tracker for " + ((Class)(serviceType)).getName() 
                                            + " using bundle-context:" + bc);
        ServiceTracker st = 
            new ServiceTracker(bc, ((Class)(serviceType)).getName(), null);
        st.open();
        try {
            Object service = ((os.waitTimeout() == -1) 
                                    ? st.getService() 
                                    : st.waitForService(os.waitTimeout()));
            debug("service obtained from tracker" + service);
            if (service == null) {
                throw new ServiceUnavailableException();
            } 
            return service;
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Unget the service
     */
    public static void ungetService(Object serviceInstance, 
                                    InjectionPoint svcInjectionPoint){
        //XXX: not implemented
        
    }
    
    private static void debug(String string) {
        if(DEBUG_ENABLED)
            System.out.println("ServiceFactory:: " + string);
    }
    
    public static class ServiceUnavailableException extends Exception {
        private static final long serialVersionUID = 4055254962336930137L;
    }
    
}
