/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.svc.event;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class CEFAuditFormatter implements AuditFormatter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CEFAuditFormatter.class );

    private static final String CEF_EXTENSION_SEPARATOR = "|";
    private static final Map<String, String> CEF_VALUE_ESCAPES;

    static
    {
        final Map<String, String> map = new LinkedHashMap<>( );
        map.put( "\\", "\\\\" );
        map.put( "=", "\\=" );
        map.put( "|", "\\|" );
        map.put( "\n", "\\n" );
        CEF_VALUE_ESCAPES = Collections.unmodifiableMap( map );
    }

    enum CEFAuditField
    {
        cat( AuditField.type ),
        act( AuditField.eventCode ),
        rt( AuditField.timestamp ),
        msg( AuditField.message ),
        reason( AuditField.narrative ),
        suid( AuditField.perpetratorID ),
        suser( AuditField.perpetratorDN ),
        src( AuditField.sourceAddress ),
        srchost( AuditField.sourceHost ),
        duid( AuditField.targetID ),
        duser( AuditField.targetDN ),
        dvchost( null ),
        dtz( null ),;

        private final AuditField auditField;

        CEFAuditField( final AuditField auditField )
        {
            this.auditField = auditField;
        }

        public AuditField getAuditField()
        {
            return auditField;
        }
    }

    @Override
    public String convertAuditRecordToMessage(
            final PwmApplication pwmApplication,
            final AuditRecord auditRecord
    )
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmApplication.getConfig();
        final Settings settings = Settings.fromConfiguration( configuration );
        final Map<String, Object> auditRecordMap = JsonUtil.deserializeMap( JsonUtil.serialize( auditRecord ) );

        final Optional<String> srcHost = JavaHelper.deriveLocalServerHostname( configuration );

        final StringBuilder cefOutput = new StringBuilder(  );

        // cef header
        cefOutput.append( makeCefHeader( pwmApplication, settings, auditRecord ) );


        cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );

        srcHost.ifPresent( s -> appendCefValue( CEFAuditField.dvchost.name(), s, cefOutput, settings ) );

        if ( StringUtil.isEmpty( settings.getCefTimezone() ) )
        {
            appendCefValue( CEFAuditField.dtz.name(), settings.getCefTimezone(), cefOutput, settings );
        }

        for ( final CEFAuditField cefAuditField : CEFAuditField.values() )
        {
            if ( cefAuditField.getAuditField() != null )
            {
                final String auditFieldName = cefAuditField.getAuditField().name();
                final Object value = auditRecordMap.get( auditFieldName );
                if ( value != null )
                {
                    final String valueString = value.toString();
                    appendCefValue( auditFieldName, valueString, cefOutput, settings );
                }
            }
        }

        final int cefLength = CEFAuditFormatter.CEF_EXTENSION_SEPARATOR.length();
        if ( cefOutput.substring( cefOutput.length() - cefLength ).equals( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR ) )
        {
            cefOutput.replace( cefOutput.length() - cefLength, cefOutput.length(), "" );
        }

        return cefOutput.toString();
    }

    private String makeCefHeader( final PwmApplication pwmApplication, final Settings settings, final AuditRecord auditRecord )
            throws PwmUnrecoverableException
    {
        final StringBuilder cefOutput = new StringBuilder(  );

        // cef declaration:version prefix
        cefOutput.append( "CEF:0" );

        // Device Vendor
        appendCefHeader( pwmApplication, cefOutput, settings.getHeaderVendor() );

        // Device Product
        appendCefHeader( pwmApplication, cefOutput, settings.getHeaderProduct() );

        // Device Version
        appendCefHeader( pwmApplication, cefOutput, PwmConstants.SERVLET_VERSION );

        // Device Event Class ID
        appendCefHeader( pwmApplication, cefOutput, String.valueOf( auditRecord.getEventCode() ) );

        // field name
        appendCefHeader( pwmApplication, cefOutput, LocaleHelper.getLocalizedMessage(
                PwmConstants.DEFAULT_LOCALE,
                auditRecord.getEventCode().getMessage(),
                pwmApplication.getConfig()
        ) );

        // severity
        appendCefHeader( pwmApplication, cefOutput, settings.getHeaderSeverity() );

        return cefOutput.toString();
    }

    private void appendCefHeader( final PwmApplication pwmApplication, final StringBuilder cefOutput, final String value )
            throws PwmUnrecoverableException
    {
        final MacroRequest macroRequest = MacroRequest.forNonUserSpecific( pwmApplication, SessionLabel.SYSTEM_LABEL );
        cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );
        cefOutput.append( macroRequest.expandMacros( value ) );
    }

    private void appendCefValue( final String name, final String value, final StringBuilder cefOutput, final Settings settings )
    {
        if ( !StringUtil.isEmpty( value ) && !StringUtil.isEmpty( name ) )
        {
            final String outputValue = trimCEFValue( name, escapeCEFValue( value ), settings );
            cefOutput.append( " " );
            cefOutput.append( name );
            cefOutput.append( "=" );
            cefOutput.append( outputValue );
        }
    }

    private String trimCEFValue ( final String name, final String value, final Settings settings )
    {
        final int cefMaxExtensionChars = settings.getCefMaxExtensionChars();
        if ( value != null && value.length() > cefMaxExtensionChars )
        {
            LOGGER.trace( () -> "truncating cef value for field '" + name + "' from " + value.length() + " to max cef length " + cefMaxExtensionChars );
        }
        return StringUtil.truncate( value, cefMaxExtensionChars );
    }

    private static String escapeCEFValue( final String value )
    {
        String replacedValue = value;
        for ( final Map.Entry<String, String> entry : CEFAuditFormatter.CEF_VALUE_ESCAPES.entrySet() )
        {
            final String pattern = entry.getKey();
            final String replacement = entry.getValue();
            replacedValue = replacedValue.replace( pattern, replacement );
        }
        return replacedValue;
    }

    @Value
    @Builder
    private static class Settings
    {
        private int cefMaxExtensionChars;
        private String cefTimezone;
        private String headerSeverity;
        private String headerProduct;
        private String headerVendor;

        static Settings fromConfiguration( final Configuration configuration )
        {
            return Settings.builder()
                    .cefMaxExtensionChars( JavaHelper.silentParseInt( configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_MAX_EXTENSION_CHARS ), 1023 ) )
                    .cefTimezone( configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_TIMEZONE ) )
                    .headerSeverity( configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_SEVERITY ) )
                    .headerProduct( configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_PRODUCT ) )
                    .headerVendor( configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_VENDOR ) )
                    .build();
        }
    }
}
