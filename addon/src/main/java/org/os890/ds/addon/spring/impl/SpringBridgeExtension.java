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

import org.apache.deltaspike.core.util.ClassUtils;
import org.apache.deltaspike.core.util.ExceptionUtils;
import org.apache.deltaspike.core.util.ServiceUtils;
import org.apache.deltaspike.core.util.bean.BeanBuilder;
import org.apache.deltaspike.core.util.bean.ImmutablePassivationCapableBean;
import org.apache.deltaspike.core.util.metadata.AnnotationInstanceProvider;
import org.apache.deltaspike.core.util.metadata.builder.AnnotatedTypeBuilder;
import org.apache.deltaspike.core.util.metadata.builder.ContextualLifecycle;
import org.os890.ds.addon.spring.spi.BeanFilter;
import org.os890.ds.addon.spring.spi.SpringContainerManager;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

//other scopes than singleton and prototype require a proper proxy-config (ScopedProxyMode.TARGET_CLASS) for spring
public class SpringBridgeExtension implements Extension
{
    private List<Bean<?>> cdiBeansForSpring = new ArrayList<Bean<?>>();
    private static ThreadLocal<List<Bean<?>>> currentCdiBeans = new ThreadLocal<List<Bean<?>>>();

    private ConfigurableApplicationContext springContext;

    private List<BeanFilter> beanFilterList = new ArrayList<BeanFilter>();

    public void init(@Observes BeforeBeanDiscovery beforeBeanDiscovery)
    {
        beanFilterList.addAll(ServiceUtils.loadServiceImplementations(BeanFilter.class));
    }

    //for supporting producers ProcessBean would be needed
    //however later on Bean#getBeanClass is used which returns the producer-class and not the return-type of the producer (like #getTypes)
    public void recordBeans(@Observes ProcessManagedBean pb)
    {
        Bean bean = pb.getBean();

        if (!isSpringAdapterBean(bean) && !isFilteredCdiBean(bean.getBeanClass()))
        {
            this.cdiBeansForSpring.add(pb.getBean());
        }
    }

    private boolean isFilteredCdiBean(Class beanClass)
    {
        for (BeanFilter beanFilter : this.beanFilterList)
        {
            if (!beanFilter.exposeCdiBeanToSpring(beanClass))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isSpringAdapterBean(Bean bean) //don't add spring-bean adapters back to spring
    {
        if (bean instanceof ImmutablePassivationCapableBean)
        {
            for (Field field : bean.getClass().getSuperclass().getDeclaredFields())
            {
                if (ContextualLifecycle.class.isAssignableFrom(field.getType()))
                {
                    field.setAccessible(true);

                    try
                    {
                        if (field.get(bean) instanceof SpringAwareBeanLifecycle)
                        {
                            return true;
                        }
                    }
                    catch (IllegalAccessException e)
                    {
                        throw ExceptionUtils.throwAsRuntimeException(e);
                    }
                }
            }
        }
        return false;
    }

    public void initContainerBridge(@Observes AfterBeanDiscovery abd, BeanManager beanManager)
    {
        this.springContext = resolveSpringContext(abd);

        if (this.springContext == null)
        {
            abd.addDefinitionError(new IllegalStateException("no spring-context found/created"));
            return;
        }

        for (String beanName : this.springContext.getBeanDefinitionNames())
        {
            BeanDefinition beanDefinition = this.springContext.getBeanFactory().getBeanDefinition(beanName);

            Class<?> beanClass = ClassUtils.tryToLoadClassForName(beanDefinition.getBeanClassName());

            if (CdiSpringScope.class.getName().equals(beanDefinition.getScope()) || isFilteredSpringBean(beanClass))
            {
                continue; //don't add cdi-beans registered in spring back to cdi
            }

            abd.addBean(createBeanAdapter(beanClass, beanName, beanDefinition, this.springContext, beanManager, abd));
        }

        this.beanFilterList.clear();
        this.cdiBeansForSpring.clear();
    }

    private boolean isFilteredSpringBean(Class<?> beanClass)
    {
        for (BeanFilter beanFilter : this.beanFilterList)
        {
            if (!beanFilter.exposeSpringBeanToCdi(beanClass))
            {
                return true;
            }
        }
        return false;
    }

    private ConfigurableApplicationContext resolveSpringContext(AfterBeanDiscovery abd)
    {
        List<SpringContainerManager> scmList = ServiceUtils.loadServiceImplementations(SpringContainerManager.class);

        BeanFactoryPostProcessor beanFactoryPostProcessor =  new CdiAwareBeanFactoryPostProcessor(cdiBeansForSpring);

        try
        {
            currentCdiBeans.set(cdiBeansForSpring);

            if (scmList.isEmpty())
            {
                return null;
            }
            if (scmList.size() == 1)
            {
                return scmList.iterator().next().bootContainer(beanFactoryPostProcessor);
            }
            if (scmList.size() > 2)
            {
                abd.addDefinitionError(new IllegalStateException(scmList.size() + " spring-context-resolvers found"));
            }
            else //2 are found -> use the custom one
            {
                for (SpringContainerManager containerManager : scmList)
                {
                    if (containerManager instanceof SimpleSpringContainerManager)
                    {
                        continue;
                    }

                    if (containerManager.isStarted())
                    {
                        return containerManager.getStartedContainer();
                    }
                    return containerManager.bootContainer(beanFactoryPostProcessor);
                }
            }
            return null;
        }
        finally
        {
            currentCdiBeans.set(null);
            currentCdiBeans.remove();
        }
    }

    private <T> Bean<T> createBeanAdapter(Class<T> beanClass, String beanName, BeanDefinition beanDefinition,
                                          ConfigurableApplicationContext applicationContext, BeanManager bm,
                                          AfterBeanDiscovery abd)
    {
        String beanScope = beanDefinition.getScope();
        ContextualLifecycle lifecycleAdapter = new SpringAwareBeanLifecycle(applicationContext, beanName, beanScope);

        List<Annotation> cdiQualifiers = tryToMapToCdiQualifier(beanName, beanDefinition, abd);

        //we don't need to handle (remove) interceptor annotations, because BeanBuilder >won't< add them (not supported)
        BeanBuilder<T> beanBuilder = new BeanBuilder<T>(bm)
                .readFromType(new AnnotatedTypeBuilder<T>().readFromType(beanClass).create())
                .name(beanName)
                .beanLifecycle(lifecycleAdapter)
                .injectionPoints(Collections.<InjectionPoint>emptySet())
                .scope(Dependent.class) //the instance (or proxy) returned by spring shouldn't bootContainer proxied
                .passivationCapable(true)
                .alternative(false)
                .nullable(true);

        if (!cdiQualifiers.isEmpty())
        {
            beanBuilder.addQualifiers(cdiQualifiers);
        }

        boolean typeObjectFound = false;
        for (Type type : beanBuilder.getTypes())
        {
            if (Object.class.equals(type))
            {
                typeObjectFound = true;
            }
        }

        if (!typeObjectFound)
        {
            beanBuilder.addType(Object.class); //java.lang.Object needs to be present (as type) in any case
        }

        return beanBuilder.create();
    }

    //TODO test it
    private List<Annotation> tryToMapToCdiQualifier(String beanName,
                                                    BeanDefinition beanDefinition,
                                                    AfterBeanDiscovery abd)
    {
        List<Annotation> cdiQualifiers = new ArrayList<Annotation>();
        if (beanDefinition instanceof AbstractBeanDefinition)
        {
            boolean unsupportedQualifierFound = false;
            for (AutowireCandidateQualifier springQualifier : ((AbstractBeanDefinition) beanDefinition).getQualifiers())
            {
                Class qualifierClass = ClassUtils.tryToLoadClassForName(springQualifier.getTypeName());

                if (qualifierClass == null)
                {
                    unsupportedQualifierFound = true;
                    break;
                }

                if (Annotation.class.isAssignableFrom(qualifierClass))
                {
                    Map<String, Object> qualifierValues = new HashMap<String, Object>();
                    String methodName;
                    Object methodValue;
                    for (Method annotationMethod : qualifierClass.getDeclaredMethods())
                    {
                        methodName = annotationMethod.getName();
                        methodValue = springQualifier.getAttribute(methodName);

                        if (methodValue != null)
                        {
                            qualifierValues.put(methodName, methodValue);
                        }

                    }

                    cdiQualifiers.add(AnnotationInstanceProvider.of(qualifierClass, qualifierValues));
                }
                else
                {
                    unsupportedQualifierFound = true;
                    break;
                }
            }
            if (unsupportedQualifierFound)
            {
                abd.addDefinitionError(new IllegalStateException(beanName + " can't be added"));
            }
        }
        return cdiQualifiers;
    }

    ApplicationContext getApplicationContext()
    {
        return springContext;
    }

    public static List<Bean<?>> getCdiBeans()
    {
        return currentCdiBeans.get();
    }
}
