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


package password.pwm.svc.email;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class EmailServerUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( EmailServerUtil.class );

    static List<EmailServer> makeEmailServersMap( final Configuration configuration )
    {
        final List<EmailServer> returnObj = new ArrayList<>(  );

        final Collection<EmailServerProfile> profiles = configuration.getEmailServerProfiles().values();

        for ( final EmailServerProfile profile : profiles )
        {
            final String id = profile.getIdentifier();
            final String address = profile.readSettingAsString( PwmSetting.EMAIL_SERVER_ADDRESS );
            final int port = (int) profile.readSettingAsLong( PwmSetting.EMAIL_SERVER_PORT );
            final String username = profile.readSettingAsString( PwmSetting.EMAIL_USERNAME );
            final PasswordData password = profile.readSettingAsPassword( PwmSetting.EMAIL_PASSWORD );
            if ( !StringUtil.isEmpty( address )
                    && port > 0
                    )
            {
                final Properties properties = makeJavaMailProps( configuration, address, port );
                final javax.mail.Session session = javax.mail.Session.getInstance( properties, null );
                final EmailServer emailServer = EmailServer.builder()
                        .id( id )
                        .host( address )
                        .port( port )
                        .username( username )
                        .password( password )
                        .javaMailProps( properties )
                        .session( session )
                        .build();
                returnObj.add( emailServer );
            }
            else
            {
                LOGGER.warn( "discarding incompletely configured email address for smtp server profile " + id );
            }
        }

        return returnObj;
    }

    private static Properties makeJavaMailProps(
            final Configuration config,
            final String host,
            final int port
    )
    {
        //Create a properties item to start setting up the mail
        final Properties props = new Properties();

        //Specify the desired SMTP server
        props.put( "mail.smtp.host", host );

        //Specify SMTP server port
        props.put( "mail.smtp.port", port );

        //Specify configured advanced settings.
        final Map<String, String> advancedSettingValues = StringUtil.convertStringListToNameValuePair( config.readSettingAsStringArray( PwmSetting.EMAIL_ADVANCED_SETTINGS ), "=" );
        props.putAll( advancedSettingValues );

        return props;
    }

    private static InternetAddress makeInternetAddress( final String input )
            throws AddressException
    {
        if ( input == null )
        {
            return null;
        }

        if ( input.matches( "^.*<.*>$" ) )
        {
            // check for format like: John Doe <jdoe@example.com>
            final String[] splitString = input.split( "<|>" );
            if ( splitString.length < 2 )
            {
                return new InternetAddress( input );
            }

            final InternetAddress address = new InternetAddress();
            address.setAddress( splitString[ 1 ].trim() );
            try
            {
                address.setPersonal( splitString[ 0 ].trim(), PwmConstants.DEFAULT_CHARSET.toString() );
            }
            catch ( UnsupportedEncodingException e )
            {
                LOGGER.error( "unsupported encoding error while parsing internet address '" + input + "', error: " + e.getMessage() );
            }
            return address;
        }
        return new InternetAddress( input );
    }

    static EmailItemBean applyMacrosToEmail( final EmailItemBean emailItem, final MacroMachine macroMachine )
    {
        final EmailItemBean expandedEmailItem;
        expandedEmailItem = new EmailItemBean(
                macroMachine.expandMacros( emailItem.getTo() ),
                macroMachine.expandMacros( emailItem.getFrom() ),
                macroMachine.expandMacros( emailItem.getSubject() ),
                macroMachine.expandMacros( emailItem.getBodyPlain() ),
                macroMachine.expandMacros( emailItem.getBodyHtml() )
        );
        return expandedEmailItem;
    }

    static EmailItemBean newEmailToAddress( final EmailItemBean emailItem, final String toAddress )
    {
        final EmailItemBean expandedEmailItem;
        expandedEmailItem = new EmailItemBean(
                toAddress,
                emailItem.getFrom(),
                emailItem.getSubject(),
                emailItem.getBodyPlain(),
                emailItem.getBodyHtml()
        );
        return expandedEmailItem;
    }

    static boolean sendIsRetryable( final Exception e )
    {
        if ( e != null )
        {
            final Throwable cause = e.getCause();
            if ( cause instanceof IOException )
            {
                LOGGER.trace( "message send failure cause is due to an IOException: " + e.getMessage() );
                return true;
            }
            if ( e instanceof PwmUnrecoverableException )
            {
                if ( ( ( PwmUnrecoverableException ) e ).getError() == PwmError.ERROR_SERVICE_UNREACHABLE )
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Message> convertEmailItemToMessages(
            final EmailItemBean emailItemBean,
            final Configuration config,
            final EmailServer emailServer
    )
            throws MessagingException
    {
        final List<Message> messages = new ArrayList<>();
        final boolean hasPlainText = emailItemBean.getBodyPlain() != null && emailItemBean.getBodyPlain().length() > 0;
        final boolean hasHtml = emailItemBean.getBodyHtml() != null && emailItemBean.getBodyHtml().length() > 0;
        final String subjectEncodingCharset = config.readAppProperty( AppProperty.SMTP_SUBJECT_ENCODING_CHARSET );

        // create a new Session object for the messagejavamail
        final String emailTo = emailItemBean.getTo();
        if ( emailTo != null )
        {
            final InternetAddress[] recipients = InternetAddress.parse( emailTo );
            for ( final InternetAddress recipient : recipients )
            {
                final MimeMessage message = new MimeMessage( emailServer.getSession() );
                message.setFrom( makeInternetAddress( emailItemBean.getFrom() ) );
                message.setRecipient( Message.RecipientType.TO, recipient );
                {
                    if ( subjectEncodingCharset != null && !subjectEncodingCharset.isEmpty() )
                    {
                        message.setSubject( emailItemBean.getSubject(), subjectEncodingCharset );
                    }
                    else
                    {
                        message.setSubject( emailItemBean.getSubject() );
                    }
                }
                message.setSentDate( new Date() );

                if ( hasPlainText && hasHtml )
                {
                    final MimeMultipart content = new MimeMultipart( "alternative" );
                    final MimeBodyPart text = new MimeBodyPart();
                    final MimeBodyPart html = new MimeBodyPart();
                    text.setContent( emailItemBean.getBodyPlain(), HttpContentType.plain.getHeaderValue() );
                    html.setContent( emailItemBean.getBodyHtml(), HttpContentType.html.getHeaderValue() );
                    content.addBodyPart( text );
                    content.addBodyPart( html );
                    message.setContent( content );
                }
                else if ( hasPlainText )
                {
                    message.setContent( emailItemBean.getBodyPlain(), HttpContentType.plain.getHeaderValue() );
                }
                else if ( hasHtml )
                {
                    message.setContent( emailItemBean.getBodyHtml(), HttpContentType.html.getHeaderValue() );
                }

                messages.add( message );
            }
        }

        return messages;
    }

    static Transport makeSmtpTransport( final EmailServer server )
            throws MessagingException, PwmUnrecoverableException
    {
        // Login to SMTP server first if both username and password is given
        final Transport transport = server.getSession().getTransport( "smtp" );

        final boolean authenticated = !StringUtil.isEmpty( server.getUsername() ) && server.getPassword() != null;

        if ( authenticated )
        {
            // create a new Session object for the message
            transport.connect(
                    server.getHost(),
                    server.getPort(),
                    server.getUsername(),
                    server.getPassword().getStringValue()
            );
        }
        else
        {
            transport.connect();
        }

        LOGGER.debug( "connected to " + server.toDebugString() + " " + ( authenticated ? "(authenticated)" : "(unauthenticated)" ) );

        return transport;
    }
}
