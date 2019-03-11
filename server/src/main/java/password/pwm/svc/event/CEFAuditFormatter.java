/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.svc.event;

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
import password.pwm.util.macro.MacroMachine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class CEFAuditFormatter implements AuditFormatter
{
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
        final String cefTimezone = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_TIMEZONE );

        final Map<String, Object> auditRecordMap = JsonUtil.deserializeMap( JsonUtil.serialize( auditRecord ) );

        final String headerSeverity = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_SEVERITY );
        final String headerProduct = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_PRODUCT );
        final String headerVendor = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_VENDOR );
        final Optional<String> srcHost = JavaHelper.deriveLocalServerHostname( configuration );

        final MacroMachine macroMachine = MacroMachine.forNonUserSpecific( pwmApplication, SessionLabel.SYSTEM_LABEL );

        final String cefFieldName = LocaleHelper.getLocalizedMessage(
                PwmConstants.DEFAULT_LOCALE,
                auditRecord.getEventCode().getMessage(),
                configuration
        );

        final StringBuilder cefOutput = new StringBuilder(  );

        // cef header
        {
            // cef declaration:version prefix
            cefOutput.append( "CEF:0" );

            // Device Vendor
            cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );
            cefOutput.append( macroMachine.expandMacros( headerVendor ) );

            // Device Product
            cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );
            cefOutput.append( macroMachine.expandMacros( headerProduct ) );

            // Device Version
            cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );
            cefOutput.append( PwmConstants.SERVLET_VERSION );

            // Device Event Class ID
            cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );
            cefOutput.append( auditRecord.getEventCode() );

            // field name
            cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );
            cefOutput.append( cefFieldName );

            // severity
            cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );
            cefOutput.append( macroMachine.expandMacros( headerSeverity ) );
        }

        cefOutput.append( CEFAuditFormatter.CEF_EXTENSION_SEPARATOR );

        srcHost.ifPresent( s -> appendCefValue( CEFAuditField.dvchost.name(), s, cefOutput ) );

        if ( StringUtil.isEmpty( cefTimezone ) )
        {
            appendCefValue( CEFAuditField.dtz.name(), cefTimezone, cefOutput );
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
                    appendCefValue( auditFieldName, valueString, cefOutput );
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

    private static void appendCefValue( final String name, final String value, final StringBuilder cefOutput )
    {
        if ( !StringUtil.isEmpty( value ) && !StringUtil.isEmpty( name ) )
        {
            cefOutput.append( " " );
            cefOutput.append( name );
            cefOutput.append( "=" );
            cefOutput.append( escapeCEFValue( value ) );
        }
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
}
