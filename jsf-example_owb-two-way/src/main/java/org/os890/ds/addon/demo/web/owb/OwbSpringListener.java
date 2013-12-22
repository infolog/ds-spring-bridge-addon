/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.os890.ds.addon.demo.web.owb;

import org.apache.deltaspike.core.api.exclude.Exclude;
import org.apache.webbeans.servlet.WebBeansConfigurationListener;
import org.os890.ds.addon.spring.spi.SpringContainerManager;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.request.RequestContextListener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

@Exclude
public class OwbSpringListener implements SpringContainerManager, ServletContextListener, ServletRequestListener, HttpSessionListener
{
    //thread-local because it's needed by the same thread during bootstrapping, but by 2 instances of this class
    private static ThreadLocal<ServletContextEvent>  sce = new ThreadLocal<ServletContextEvent>();

    private WebBeansConfigurationListener owbListener = new WebBeansConfigurationListener();
    private ContextLoaderListener springContextListener = new ContextLoaderListener();
    private RequestContextListener springRequestListener = new RequestContextListener();

    private ConfigurableApplicationContext configurableApplicationContext;

    @Override
    public boolean isStarted()
    {
        return this.configurableApplicationContext != null;
    }

    @Override
    public ConfigurableApplicationContext getStartedContainer()
    {
        return configurableApplicationContext;
    }

    @Override
    public ConfigurableApplicationContext bootContainer(BeanFactoryPostProcessor... beanFactoryPostProcessors)
    {
        //lazy start of spring - needed because CdiAwareApplicationContextInitializer needs to get the found cdi-beans
        springContextListener.contextInitialized(sce.get());

        configurableApplicationContext = (ConfigurableApplicationContext)ContextLoader.getCurrentWebApplicationContext();
        return configurableApplicationContext;
    }

    @Override
    public void contextInitialized(ServletContextEvent event)
    {
        sce.set(event); //the next call will trigger OwbSpringListener#bootContainer (but on a different instance)
        try
        {
            this.owbListener.contextInitialized(event); //start owb ->
        }
        finally
        {
            sce.set(null);
            sce.remove();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce)
    {
        springContextListener.contextDestroyed(sce);
        owbListener.contextDestroyed(sce);
    }

    @Override
    public void requestInitialized(ServletRequestEvent event)
    {
        springRequestListener.requestInitialized(event);
        owbListener.requestInitialized(event);
    }

    @Override
    public void requestDestroyed(ServletRequestEvent event)
    {
        springRequestListener.requestDestroyed(event);
        owbListener.requestDestroyed(event);
    }

    @Override
    public void sessionCreated(HttpSessionEvent event)
    {
        owbListener.sessionCreated(event);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event)
    {
        owbListener.sessionDestroyed(event);
    }
}
