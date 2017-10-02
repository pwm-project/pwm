/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.http.servlet;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.JavaHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ControlledPwmServletTest {
    @Test
    public void testProcess() throws IllegalAccessException, InstantiationException {
        final Map<Class<? extends ControlledPwmServlet>,Map<String,Method>> dataMap = getClassAndMethods();

        for (final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet()) {
            final Class<? extends AbstractPwmServlet.ProcessAction> processActionsClass = controlledPwmServlet.newInstance().getProcessActionsClass();
            if (!processActionsClass.isEnum()) {
                Assert.fail(controlledPwmServlet.getName() + " process action class must be an enum");
            }
        }
    }

    @Test
    public void testActionHandlerReturnTypes() throws IllegalAccessException, InstantiationException {
        final Map<Class<? extends ControlledPwmServlet>,Map<String,Method>> dataMap = getClassAndMethods();

        for (final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet()) {
            final String servletName = controlledPwmServlet.getName();
            for (final String methodName : dataMap.get(controlledPwmServlet).keySet()) {
                final Method method = dataMap.get(controlledPwmServlet).get(methodName);
                if (method.getReturnType() != ProcessStatus.class) {
                    Assert.fail(servletName + ":" + method.getName() + " must have return type of " + ProcessStatus.class.getName());
                }
            }
        }
    }

    @Test
    public void testActionHandlerParameters() throws IllegalAccessException, InstantiationException {
        final Map<Class<? extends ControlledPwmServlet>,Map<String,Method>> dataMap = getClassAndMethods();

        for (final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet()) {
            final String servletName = controlledPwmServlet.getName();
            for (final String methodName : dataMap.get(controlledPwmServlet).keySet()) {
                final Method method = dataMap.get(controlledPwmServlet).get(methodName);
                final Class[] returnTypes = method.getParameterTypes();
                if (returnTypes.length != 1) {
                    Assert.fail(servletName + ":" + method.getName() + " must have exactly one parameter");
                }
                if (!returnTypes[0].equals(PwmRequest.class)) {
                    Assert.fail(servletName + ":" + method.getName() + " must have exactly one parameter of type " + PwmRequest.class.getName());
                }
            }
        }
    }

    @Test
    public void testActionHandlerMethodNaming() throws IllegalAccessException, InstantiationException {
        final Map<Class<? extends ControlledPwmServlet>,Map<String,Method>> dataMap = getClassAndMethods();

        for (final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet()) {
            final String servletName = controlledPwmServlet.getName();
            for (final Method method : JavaHelper.getAllMethodsForClass(controlledPwmServlet)) {
                final String methodName = method.getName();
                final ControlledPwmServlet.ActionHandler actionHandler = method.getAnnotation(ControlledPwmServlet.ActionHandler.class);
                if (actionHandler != null) {
                    final String actionName = actionHandler.action();
                    if (!methodName.toLowerCase().contains(actionName.toLowerCase())) {
                        Assert.fail("method " + servletName + ":" + methodName + " must have the ActionHandler name '"
                                + actionName + "' as part of the method name.");
                    }
                }
            }
        }
    }


    @Test
    public void testActionHandlersExistence() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        final Map<Class<? extends ControlledPwmServlet>,Map<String,Method>> dataMap = getClassAndMethods();

        for (final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet()) {
            final String servletName = controlledPwmServlet.getName();

            final Class<? extends AbstractPwmServlet.ProcessAction> processActionsClass = controlledPwmServlet.newInstance().getProcessActionsClass();
            final List<String> names = new ArrayList<>();
            for (Object enumObject : processActionsClass.getEnumConstants()) {
                names.add(((Enum)enumObject).name());
            }

            {
                final Collection<String> missingActionHandlers = new HashSet<>(names);
                missingActionHandlers.removeAll(dataMap.get(controlledPwmServlet).keySet());
                if (!missingActionHandlers.isEmpty()) {
                    Assert.fail(servletName + " does not have an action handler for action " + missingActionHandlers.iterator().next());
                }
            }

            {
                final Collection<String> superflousActionHandlers = new HashSet<>(dataMap.get(controlledPwmServlet).keySet());
                superflousActionHandlers.removeAll(names);
                if (!superflousActionHandlers.isEmpty()) {
                    Assert.fail(servletName + " has an action handler for action " + superflousActionHandlers.iterator().next() + " but no such ProcessAction exists");
                }
            }
        }
    }

    private Map<Class<? extends ControlledPwmServlet>,Map<String,Method>> getClassAndMethods() {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("password.pwm"))
                .setScanners(new SubTypesScanner(),
                        new TypeAnnotationsScanner(),
                        new FieldAnnotationsScanner()
                ));


        Set<Class<? extends ControlledPwmServlet>> classes = reflections.getSubTypesOf(ControlledPwmServlet.class);

        final Map<Class<? extends ControlledPwmServlet>,Map<String,Method>> returnMap = new HashMap<>();

        for (final Class<? extends ControlledPwmServlet> controlledPwmServlet : classes) {
            if (!Modifier.isAbstract(controlledPwmServlet.getModifiers())) {

                final Map<String, Method> annotatedMethods = new HashMap<>();

                for (Method method : JavaHelper.getAllMethodsForClass(controlledPwmServlet)) {
                    if (method.getAnnotation(ControlledPwmServlet.ActionHandler.class) != null) {
                        final String actionName = method.getAnnotation(ControlledPwmServlet.ActionHandler.class).action();
                        annotatedMethods.put(actionName, method);
                    }
                }

                returnMap.put(controlledPwmServlet, Collections.unmodifiableMap(annotatedMethods));
            }
        }

        return Collections.unmodifiableMap(returnMap);
    }
}
