/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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


package password.pwm.svc.email;

import com.sun.mail.smtp.SMTPSendFailedException;
import com.sun.mail.util.MailSSLSocketFactory;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SmtpServerType;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.PwmTrustManager;
import password.pwm.util.secure.CertificateReadingTrustManager;
import password.pwm.util.secure.X509Utils;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public class EmailServerUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( EmailServerUtil.class );

    static List<EmailServer> makeEmailServersMap( final Configuration configuration )
            throws PwmUnrecoverableException
    {
        final List<EmailServer> returnObj = new ArrayList<>(  );

        final Collection<EmailServerProfile> profiles = configuration.getEmailServerProfiles().values();

        for ( final EmailServerProfile profile : profiles )
        {
            final TrustManager[] trustManager = trustManagerForProfile( configuration, profile );

            final Optional<EmailServer> emailServer = makeEmailServer( configuration, profile, trustManager );

            emailServer.ifPresent( returnObj::add );
        }

        return returnObj;
    }

    private static Optional<EmailServer> makeEmailServer(
            final Configuration configuration,
            final EmailServerProfile profile,
            final TrustManager[] trustManagers
    )
            throws PwmUnrecoverableException
    {
        final String id = profile.getIdentifier();
        final String address = profile.readSettingAsString( PwmSetting.EMAIL_SERVER_ADDRESS );
        final int port = (int) profile.readSettingAsLong( PwmSetting.EMAIL_SERVER_PORT );
        final String username = profile.readSettingAsString( PwmSetting.EMAIL_USERNAME );
        final PasswordData password = profile.readSettingAsPassword( PwmSetting.EMAIL_PASSWORD );

        final SmtpServerType smtpServerType = profile.readSettingAsEnum( PwmSetting.EMAIL_SERVER_TYPE, SmtpServerType.class );
        if ( !StringUtil.isEmpty( address )
                && port > 0
        )
        {
            final Properties properties = makeJavaMailProps( configuration, profile, trustManagers );
            final javax.mail.Session session = javax.mail.Session.getInstance( properties, null );
            return Optional.of( EmailServer.builder()
                    .id( id )
                    .host( address )
                    .port( port )
                    .username( username )
                    .password( password )
                    .javaMailProps( properties )
                    .session( session )
                    .type( smtpServerType )
                    .build() );
        }
        else
        {
            LOGGER.warn( () -> "discarding incompletely configured email address for smtp server profile " + id );
        }

        return Optional.empty();
    }

    private static TrustManager[] trustManagerForProfile( final Configuration configuration, final EmailServerProfile emailServerProfile )
            throws PwmUnrecoverableException
    {
        final List<X509Certificate> configuredCerts = emailServerProfile.readSettingAsCertificate( PwmSetting.EMAIL_SERVER_CERTS );
        if ( JavaHelper.isEmpty( configuredCerts ) )
        {
            return X509Utils.getDefaultJavaTrustManager( configuration );
        }
        final TrustManager certMatchingTrustManager = PwmTrustManager.createPwmTrustManager( configuration, configuredCerts );
        return new TrustManager[]
                {
                        certMatchingTrustManager,
                };
    }


    private static Properties makeJavaMailProps(
            final Configuration config,
            final EmailServerProfile profile,
            final TrustManager[] trustManager
    )
            throws PwmUnrecoverableException
    {
        //Create a properties item to start setting up the mail
        final Properties properties = new Properties();

        //Specify the desired SMTP server
        final String address = profile.readSettingAsString( PwmSetting.EMAIL_SERVER_ADDRESS );
        properties.put( "mail.smtp.host", address );

        //Specify SMTP server port
        final int port = (int) profile.readSettingAsLong( PwmSetting.EMAIL_SERVER_PORT );
        properties.put( "mail.smtp.port", port );
        properties.put( "mail.smtp.socketFactory.port", port );

        //set connection properties
        properties.put( "mail.smtp.connectiontimeout", JavaHelper.silentParseInt( config.readAppProperty( AppProperty.SMTP_IO_CONNECT_TIMEOUT ), 10_000 ) );
        properties.put( "mail.smtp.timeout", JavaHelper.silentParseInt( config.readAppProperty( AppProperty.SMTP_IO_CONNECT_TIMEOUT ), 30_000 ) );

        properties.put( "mail.smtp.sendpartial", true );

        try
        {
            final SmtpServerType smtpServerType = profile.readSettingAsEnum( PwmSetting.EMAIL_SERVER_TYPE, SmtpServerType.class );
            if ( smtpServerType == SmtpServerType.SMTP )
            {
                return properties;
            }

            final MailSSLSocketFactory mailSSLSocketFactory = new MailSSLSocketFactory();
            mailSSLSocketFactory.setTrustManagers( trustManager );

            properties.put( "mail.smtp.ssl.enable", true );
            properties.put( "mail.smtp.ssl.checkserveridentity", true );
            properties.put( "mail.smtp.socketFactory.fallback", false );
            properties.put( "mail.smtp.ssl.socketFactory", mailSSLSocketFactory );
            properties.put( "mail.smtp.ssl.socketFactory.port", port );

            final boolean useStartTls = smtpServerType == SmtpServerType.START_TLS;
            properties.put( "mail.smtp.starttls.enable", useStartTls );
            properties.put( "mail.smtp.starttls.required", useStartTls );
        }
        catch ( final Exception e )
        {
            final String msg = "unable to create message transport properties: " + e.getMessage();
            throw new PwmUnrecoverableException( PwmError.CONFIG_FORMAT_ERROR, msg );
        }

        //Specify configured advanced settings.
        final Map<String, String> advancedSettingValues = StringUtil.convertStringListToNameValuePair( config.readSettingAsStringArray( PwmSetting.EMAIL_ADVANCED_SETTINGS ), "=" );
        properties.putAll( advancedSettingValues );

        return properties;
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
            catch ( final UnsupportedEncodingException e )
            {
                LOGGER.error( () -> "unsupported encoding error while parsing internet address '" + input + "', error: " + e.getMessage() );
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

    static boolean examineSendFailure( final Exception e, final Set<Integer> retyableStatusCodes )
    {
        if ( e != null )
        {
            {
                final Optional<IOException> optionalIoException = JavaHelper.extractNestedExceptionType( e, IOException.class );
                if ( optionalIoException.isPresent() )
                {
                    LOGGER.trace( () -> "message send failure cause is due to an I/O error: " + optionalIoException.get().getMessage() );
                    return true;
                }
            }

            {
                final Optional<SMTPSendFailedException> optionalSmtpSendFailedException = JavaHelper.extractNestedExceptionType( e, SMTPSendFailedException.class );
                if ( optionalSmtpSendFailedException.isPresent() )
                {
                    final SMTPSendFailedException smtpSendFailedException = optionalSmtpSendFailedException.get();
                    final int returnCode = smtpSendFailedException.getReturnCode();
                    LOGGER.trace( () -> "message send failure cause is due to server response code: " + returnCode );
                    if ( retyableStatusCodes.contains( returnCode ) )
                    {
                        return true;
                    }
                }
            }

            if ( e instanceof PwmUnrecoverableException )
            {
                return ( ( PwmUnrecoverableException ) e ).getError() == PwmError.ERROR_SERVICE_UNREACHABLE;
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
                    text.setContent( emailItemBean.getBodyPlain(), HttpContentType.plain.getHeaderValueWithEncoding() );
                    html.setContent( emailItemBean.getBodyHtml(), HttpContentType.html.getHeaderValueWithEncoding() );
                    content.addBodyPart( text );
                    content.addBodyPart( html );
                    message.setContent( content );
                }
                else if ( hasPlainText )
                {
                    message.setContent( emailItemBean.getBodyPlain(), HttpContentType.plain.getHeaderValueWithEncoding() );
                }
                else if ( hasHtml )
                {
                    message.setContent( emailItemBean.getBodyHtml(), HttpContentType.html.getHeaderValueWithEncoding() );
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
        final boolean authenticated = !StringUtil.isEmpty( server.getUsername() ) && server.getPassword() != null;

        final Transport transport = server.getSession().getTransport( );

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

        LOGGER.debug( () -> "connected to " + server.toDebugString() + " " + ( authenticated ? "(authenticated)" : "(unauthenticated)" ) );

        return transport;
    }


    public static List<X509Certificate> readCertificates( final Configuration configuration, final String profile )
            throws PwmUnrecoverableException
    {
        final EmailServerProfile emailServerProfile = configuration.getEmailServerProfiles().get( profile );
        final CertificateReadingTrustManager certReaderTm = CertificateReadingTrustManager.newCertReaderTrustManager(
                configuration,
                X509Utils.ReadCertificateFlag.ReadOnlyRootCA );
        final TrustManager[] trustManagers =  new TrustManager[]
                {
                        certReaderTm,
                };
        final Optional<EmailServer> emailServer = makeEmailServer( configuration, emailServerProfile, trustManagers );
        if ( emailServer.isPresent() )
        {
            try ( Transport transport = makeSmtpTransport( emailServer.get() ); )
            {
                return certReaderTm.getCertificates();
            }
            catch ( final Exception e )
            {
                final String exceptionMessage = JavaHelper.readHostileExceptionMessage( e );
                final String errorMsg = "error connecting to secure server while reading SMTP certificates: " + exceptionMessage;
                LOGGER.debug( () -> errorMsg );
                throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_UNREACHABLE, errorMsg );
            }
        }

        return Collections.emptyList();
    }

}
