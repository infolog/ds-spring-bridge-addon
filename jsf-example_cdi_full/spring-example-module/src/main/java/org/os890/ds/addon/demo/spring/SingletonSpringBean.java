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
package org.os890.ds.addon.demo.spring;

import org.os890.ds.addon.demo.spring.cdi.ApplicationScopedCdiBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
//@Lazy is important - if cdi injection is needed
// (because bean-creation and injection would happen before the cdi-container is ready -
// since the spring-container gets started during the bootstrapping of the cdi-container)
public class SingletonSpringBean
{
    @Autowired
    private ApplicationScopedCdiBean applicationScopedCdiBean;

    public String getValue()
    {
        return getClass().getName() + " <- " + this.applicationScopedCdiBean.getValue();
    }
}
