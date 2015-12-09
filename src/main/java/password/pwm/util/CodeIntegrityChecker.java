/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.error.PwmError;
import password.pwm.health.HealthMessage;
import password.pwm.i18n.Message;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.rest.bean.HealthRecord;

import java.lang.reflect.Method;
import java.util.*;

public class CodeIntegrityChecker {
    final static private PwmLogger LOGGER = PwmLogger.forClass(CodeIntegrityChecker.class);
    final static private boolean debugFlag = false;


    private static final Map<Method,Object[]> CHECK_ENUM_METHODS = new LinkedHashMap<>();
    static {
        try {
            CHECK_ENUM_METHODS.put(PwmSetting.class.getMethod("getDescription", Locale.class), new Object[]{
                    PwmConstants.DEFAULT_LOCALE
            });
            CHECK_ENUM_METHODS.put(PwmSetting.class.getMethod("getDefaultValue", PwmSettingTemplate.class), new Object[]{
                    PwmSettingTemplate.DEFAULT
            });


            CHECK_ENUM_METHODS.put(AppProperty.class.getMethod("getDefaultValue"), new Object[]{
            });

            CHECK_ENUM_METHODS.put(PwmError.class.getMethod("getLocalizedMessage", Locale.class, Configuration.class, String[].class), new Object[]{
                    PwmConstants.DEFAULT_LOCALE, null, null
            });
            CHECK_ENUM_METHODS.put(Message.class.getMethod("getLocalizedMessage", Locale.class, Configuration.class, String[].class), new Object[]{
                    PwmConstants.DEFAULT_LOCALE, null, null
            });
        } catch (NoSuchMethodException e) {
            final String message = CodeIntegrityChecker.class.getSimpleName() + " error setting up static check components: " + e.getMessage();
            System.err.print(message);
            System.out.print(message);
            System.exit(-1);
        }
    }

    public CodeIntegrityChecker() {
    }

    public Set<password.pwm.health.HealthRecord> checkResources() {
        final Set<password.pwm.health.HealthRecord> returnSet = new TreeSet<>();
        returnSet.addAll(checkEnumMethods());
        return returnSet;
    }

    private Set<password.pwm.health.HealthRecord> checkEnumMethods() {
        final Set<password.pwm.health.HealthRecord> returnSet = new LinkedHashSet<>();
        for (final Method method : CHECK_ENUM_METHODS.keySet()) {
            final Object[] arguments = CHECK_ENUM_METHODS.get(method);
            returnSet.addAll(checkEnumMethods(method,arguments));
        }
        return returnSet;
    }

    private Set<password.pwm.health.HealthRecord> checkEnumMethods(final Method enumMethod, final Object[] arguments)
    {
        final Set<password.pwm.health.HealthRecord> returnRecords = new LinkedHashSet<>();
        try {
            final Method enumValuesMethod = enumMethod.getDeclaringClass().getMethod("values");
            final Object[] enumValues = (Object[])enumValuesMethod.invoke(null);
            for (final Object enumValue : enumValues) {
                try {
                    enumMethod.invoke(enumValue,arguments);
                } catch (Exception e) {
                    final Throwable cause = e.getCause();
                    final String errorMsg = cause != null ? cause.getMessage() != null ? cause.getMessage() : cause.toString() : e.getMessage();
                    final StringBuilder methodName = new StringBuilder();
                    methodName.append(enumMethod.getDeclaringClass().getName()).append(".").append(enumValue.toString()).append(":").append(enumMethod.getName()).append("(");
                    for (int i = 0; i < enumMethod.getParameterTypes().length; i++) {
                        methodName.append(enumMethod.getParameterTypes()[i].getSimpleName());
                        if (i < (enumMethod.getParameterTypes().length-1)) {
                            methodName.append(",");
                        }
                    }
                    methodName.append(")");
                    returnRecords.add(password.pwm.health.HealthRecord.forMessage(HealthMessage.BrokenMethod,
                            methodName.toString(), errorMsg));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return returnRecords;
    }


    public String asPrettyJsonOutput() {
        final Map<String,Object> outputMap = new LinkedHashMap<>();
        outputMap.put("information",
                PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " IntegrityCheck " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(
                        new Date()));
        {
            final Set<HealthRecord> healthBeans = new LinkedHashSet<>();
            for (final password.pwm.health.HealthRecord record : this.checkEnumMethods()) {
                healthBeans.add(
                        HealthRecord.fromHealthRecord(record, PwmConstants.DEFAULT_LOCALE, null));
            }
            outputMap.put("enumMethodHealthChecks", healthBeans);
        }
        return JsonUtil.serializeMap(outputMap, JsonUtil.Flag.PrettyPrint);
    }
}
