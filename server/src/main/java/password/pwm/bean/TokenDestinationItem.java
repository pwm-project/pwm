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

package password.pwm.bean;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.token.TokenDestinationDisplayMasker;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.SecureService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Value
@Builder
public class TokenDestinationItem implements Serializable
{
    private static final Map<PwmSetting, TokenDestinationItem.Type> SETTING_TO_DEST_TYPE_MAP;

    static
    {
        final Map<PwmSetting, TokenDestinationItem.Type> tempMap = new HashMap<>(  );
        tempMap.put( PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE, TokenDestinationItem.Type.email );
        tempMap.put( PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE_2, TokenDestinationItem.Type.email );
        tempMap.put( PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE_3, TokenDestinationItem.Type.email );
        tempMap.put( PwmSetting.SMS_USER_PHONE_ATTRIBUTE, TokenDestinationItem.Type.sms );
        tempMap.put( PwmSetting.SMS_USER_PHONE_ATTRIBUTE_2, TokenDestinationItem.Type.sms );
        tempMap.put( PwmSetting.SMS_USER_PHONE_ATTRIBUTE_3, TokenDestinationItem.Type.sms );
        SETTING_TO_DEST_TYPE_MAP = Collections.unmodifiableMap( tempMap );
    }

    public static Map<PwmSetting, Type> getSettingToDestTypeMap( )
    {
        return SETTING_TO_DEST_TYPE_MAP;
    }

    private String id;
    private String display;
    private String value;
    private Type type;

    public enum Type
    {
        sms( MessageSendMethod.SMSONLY ),
        email( MessageSendMethod.EMAILONLY ),;

        private MessageSendMethod messageSendMethod;

        Type( final MessageSendMethod messageSendMethod )
        {
            this.messageSendMethod = messageSendMethod;
        }

        public MessageSendMethod getMessageSendMethod( )
        {
            return messageSendMethod;
        }
    }

    public static List<TokenDestinationItem> allFromConfig(
            final PwmApplication pwmApplication,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmApplication.getConfig();
        final SecureService secureService = pwmApplication.getSecureService();

        final TokenDestinationDisplayMasker tokenDestinationDisplayMasker = new TokenDestinationDisplayMasker( configuration );

        final Map<String, TokenDestinationItem> results = new LinkedHashMap<>(  );

        for ( final String emailValue : new String[]
                {
                        userInfo.getUserEmailAddress(),
                        userInfo.getUserEmailAddress2(),
                        userInfo.getUserEmailAddress3(),
                }
                )
        {
            if ( !StringUtil.isEmpty( emailValue ) )
            {
                final String idHash = secureService.hash( emailValue + Type.email.name() );
                final TokenDestinationItem item = TokenDestinationItem.builder()
                        .id( idHash )
                        .display( tokenDestinationDisplayMasker.maskEmail( emailValue ) )
                        .value( emailValue )
                        .type( Type.email )
                        .build();
                results.put( idHash, item );
            }
        }

        for ( final String smsValue : new String[]
                {
                        userInfo.getUserSmsNumber(),
                        userInfo.getUserSmsNumber2(),
                        userInfo.getUserSmsNumber3(),
                }
                )
        {
            if ( !StringUtil.isEmpty( smsValue ) )
            {
                final String idHash = secureService.hash( smsValue + Type.sms.name() );
                final TokenDestinationItem item = TokenDestinationItem.builder()
                        .id( idHash )
                        .display( tokenDestinationDisplayMasker.maskPhone( smsValue ) )
                        .value( smsValue )
                        .type( Type.sms )
                        .build();
                results.put(  idHash, item );
            }
        }

        return Collections.unmodifiableList( new ArrayList<>( results.values() ) );
    }

    public static Optional<TokenDestinationItem> tokenDestinationItemForID(
            final Collection<TokenDestinationItem> tokenDestinationItems,
            final String requestedID
    )
    {
        if ( tokenDestinationItems == null || requestedID == null )
        {
            return Optional.empty();
        }

        for ( final TokenDestinationItem item : tokenDestinationItems )
        {
            if ( requestedID.equals( item.getId() ) )
            {
                return Optional.of( item );
            }
        }

        return Optional.empty();
    }

    public static List<TokenDestinationItem> stripValues( final List<TokenDestinationItem> input )
    {
        final List<TokenDestinationItem> returnList = new ArrayList<>();
        if ( input != null )
        {
            for ( final TokenDestinationItem item : input )
            {
                final TokenDestinationItem newItem = TokenDestinationItem.builder()
                        .display( item.display )
                        .id( item.id )
                        .type ( item.type )
                        .build();
                returnList.add( newItem );
            }
        }
        return returnList;
    }
}
