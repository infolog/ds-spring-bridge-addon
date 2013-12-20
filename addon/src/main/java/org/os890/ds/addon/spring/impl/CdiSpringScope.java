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
package org.os890.ds.addon.spring.impl;

import org.apache.deltaspike.core.api.provider.BeanProvider;
import org.apache.deltaspike.core.util.ClassUtils;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

public class CdiSpringScope implements Scope
{
    public Object get(String name, ObjectFactory objectFactory)
    {
        if (name.contains("#"))
        {
            name = name.substring(0, name.indexOf("#")); //needed for ApplicationContext#getBean(class)
        }

        Class<?> beanClass = ClassUtils.tryToLoadClassForName(name);

        if (beanClass != null)
        {
            return BeanProvider.getContextualReference(beanClass);
        }
        return BeanProvider.getContextualReference(name);
    }

    public Object remove(String name)
    {
        //not needed
        //cdi will handle normal-scoped beans and for dependent-scoped beans we don't have the instance to destroy
        return null;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback)
    {
        //not needed - done by the cdi-container
    }

    @Override
    public Object resolveContextualObject(String key)
    {
        return null; //not needed
    }

    @Override
    public String getConversationId()
    {
        return null; //not needed
    }
}
