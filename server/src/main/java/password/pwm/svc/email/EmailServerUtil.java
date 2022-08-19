/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.ProfileID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SmtpServerType;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.http.HttpContentType;
import password.pwm.util.PasswordData;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.CertificateReadingTrustManager;
import password.pwm.util.secure.PwmTrustManager;
import password.pwm.util.secure.X509Utils;

import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
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

    static List<EmailServer> makeEmailServersMap( final AppConfig appConfig )
            throws PwmUnrecoverableException
    {
        final List<EmailServer> returnObj = new ArrayList<>(  );

        final Collection<EmailServerProfile> profiles = appConfig.getEmailServerProfiles().values();

        for ( final EmailServerProfile profile : profiles )
        {
            final TrustManager[] trustManager = trustManagerForProfile( appConfig, profile );

            final Optional<EmailServer> emailServer = makeEmailServer( appConfig, profile, trustManager );

            emailServer.ifPresent( returnObj::add );
        }

        return returnObj;
    }

    public static Optional<EmailServer> makeEmailServer(
            final AppConfig appConfig,
            final EmailServerProfile profile,
            final TrustManager[] trustManagers
    )
            throws PwmUnrecoverableException
    {
        final ProfileID id = profile.getId();
        final String address = profile.readSettingAsString( PwmSetting.EMAIL_SERVER_ADDRESS );
        final int port = (int) profile.readSettingAsLong( PwmSetting.EMAIL_SERVER_PORT );
        final String username = profile.readSettingAsString( PwmSetting.EMAIL_USERNAME );
        final PasswordData password = profile.readSettingAsPassword( PwmSetting.EMAIL_PASSWORD );

        final SmtpServerType smtpServerType = profile.readSettingAsEnum( PwmSetting.EMAIL_SERVER_TYPE, SmtpServerType.class );
        if ( StringUtil.notEmpty( address )
                && port > 0
        )
        {
            final TrustManager[] effectiveTrustManagers = trustManagers == null
                    ? trustManagerForProfile( appConfig, profile )
                    : trustManagers;
            final Properties properties = makeJavaMailProps( appConfig, profile, effectiveTrustManagers );
            final jakarta.mail.Session session = jakarta.mail.Session.getInstance( properties, null );
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

    private static TrustManager[] trustManagerForProfile( final AppConfig appConfig, final EmailServerProfile emailServerProfile )
            throws PwmUnrecoverableException
    {
        final List<X509Certificate> configuredCerts = emailServerProfile.readSettingAsCertificate( PwmSetting.EMAIL_SERVER_CERTS );
        if ( CollectionUtil.isEmpty( configuredCerts ) )
        {
            return X509Utils.getDefaultJavaTrustManager( appConfig );
        }
        final TrustManager certMatchingTrustManager = PwmTrustManager.createPwmTrustManager( appConfig, configuredCerts );
        return new TrustManager[]
                {
                        certMatchingTrustManager,
                };
    }


    private static Properties makeJavaMailProps(
            final AppConfig config,
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
            properties.put( "mail.smtp.ssl.socketFactory", mailSSLSocketFactory );

            if ( smtpServerType == SmtpServerType.SMTPS )
            {
                properties.put( "mail.smtp.ssl.enable", true );
                properties.put( "mail.smtp.ssl.checkserveridentity", true );
                properties.put( "mail.smtp.socketFactory.fallback", false );
                properties.put( "mail.smtp.ssl.socketFactory.port", port );
            }
            else if ( smtpServerType == SmtpServerType.START_TLS )
            {
                properties.put( "mail.smtp.starttls.enable", true );
                properties.put( "mail.smtp.starttls.required", true );
            }
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

    private static Optional<InternetAddress> makeInternetAddress(
            final String input,
            final SessionLabel sessionLabel
    )
            throws AddressException
    {
        if ( input == null )
        {
            return Optional.empty();
        }

        if ( input.matches( "^.*<.*>$" ) )
        {
            // check for format like: John Doe <jdoe@example.com>
            final String[] splitString = input.split( "<|>" );
            if ( splitString.length < 2 )
            {
                return Optional.of( new InternetAddress( input ) );
            }

            final InternetAddress address = new InternetAddress();
            address.setAddress( splitString[ 1 ].trim() );
            try
            {
                address.setPersonal( splitString[ 0 ].trim(), PwmConstants.DEFAULT_CHARSET.toString() );
            }
            catch ( final UnsupportedEncodingException e )
            {
                LOGGER.error( sessionLabel, () -> "unsupported encoding error while parsing internet address '" + input + "', error: " + e.getMessage() );
            }
            return Optional.of( address );
        }
        return Optional.of( new InternetAddress( input ) );
    }

    static EmailItemBean applyMacrosToEmail( final EmailItemBean emailItem, final MacroRequest macroRequest )
    {
        return new EmailItemBean(
                macroRequest.expandMacros( emailItem.getTo() ),
                macroRequest.expandMacros( emailItem.getFrom() ),
                macroRequest.expandMacros( emailItem.getSubject() ),
                macroRequest.expandMacros( emailItem.getBodyPlain() ),
                macroRequest.expandMacros( emailItem.getBodyHtml() )
        );
    }

    static EmailItemBean newEmailToAddress( final EmailItemBean emailItem, final String toAddress )
    {
        return new EmailItemBean(
                toAddress,
                emailItem.getFrom(),
                emailItem.getSubject(),
                emailItem.getBodyPlain(),
                emailItem.getBodyHtml()
        );
    }

    static boolean examineSendFailure(
            final Exception e,
            final Set<Integer> retyableStatusCodes,
            final SessionLabel sessionLabel
    )
    {
        if ( e != null )
        {
            {
                final Optional<IOException> optionalIoException = JavaHelper.extractNestedExceptionType( e, IOException.class );
                if ( optionalIoException.isPresent() )
                {
                    LOGGER.trace( sessionLabel, () -> "message send failure cause is due to an I/O error: " + optionalIoException.get().getMessage() );
                    return true;
                }
            }

            {
                final Optional<SMTPSendFailedException> optionalSmtpSendFailedException = JavaHelper.extractNestedExceptionType( e, SMTPSendFailedException.class );
                if ( optionalSmtpSendFailedException.isPresent() )
                {
                    final SMTPSendFailedException smtpSendFailedException = optionalSmtpSendFailedException.get();
                    final int returnCode = smtpSendFailedException.getReturnCode();
                    LOGGER.trace( sessionLabel, () -> "message send failure cause is due to server response code: " + returnCode );
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
            final AppConfig config,
            final EmailServer emailServer,
            final SessionLabel sessionLabel
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

                final Optional<InternetAddress> fromAddress = makeInternetAddress( emailItemBean.getFrom(), sessionLabel );
                if ( fromAddress.isPresent() )
                {
                    message.setFrom( fromAddress.get() );
                }

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

    static Transport makeSmtpTransport(
            final EmailServer server,
            final SessionLabel sessionLabel
    )
            throws MessagingException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        // Login to SMTP server first if both username and password is given
        final boolean authenticated = StringUtil.notEmpty( server.getUsername() ) && server.getPassword() != null;

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

        LOGGER.debug( sessionLabel, () -> "connected to " + server.toDebugString() + " " + ( authenticated ? "(authenticated)" : "(unauthenticated)" ),
                TimeDuration.fromCurrent( startTime ) );

        return transport;
    }


    public static List<X509Certificate> readCertificates(
            final AppConfig appConfig,
            final ProfileID profile,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        final EmailServerProfile emailServerProfile = appConfig.getEmailServerProfiles().get( profile );
        final CertificateReadingTrustManager certReaderTm = CertificateReadingTrustManager.newCertReaderTrustManager(
                appConfig,
                X509Utils.ReadCertificateFlag.ReadOnlyRootCA );
        final TrustManager[] trustManagers =  new TrustManager[]
                {
                        certReaderTm,
                };
        final Optional<EmailServer> emailServer = makeEmailServer( appConfig, emailServerProfile, trustManagers );
        if ( emailServer.isPresent() )
        {
            try ( Transport transport = makeSmtpTransport( emailServer.get(), sessionLabel ) )
            {
                return certReaderTm.getCertificates();
            }
            catch ( final Exception e )
            {
                final String exceptionMessage = JavaHelper.readHostileExceptionMessage( e );
                final String errorMsg = "error connecting to secure server while reading SMTP certificates: " + exceptionMessage;
                LOGGER.debug( sessionLabel, () -> errorMsg );
                throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_UNREACHABLE, errorMsg );
            }
        }

        return Collections.emptyList();
    }

    static List<HealthRecord> checkAllConfiguredServers(
            final List<EmailServer> emailServers,
            final SessionLabel sessionLabel
    )
    {
        final List<HealthRecord> records = new ArrayList<>();
        for ( final EmailServer emailServer : emailServers )
        {
            try
            {
                final Transport transport = EmailServerUtil.makeSmtpTransport( emailServer, sessionLabel );
                if ( !transport.isConnected() )
                {
                    records.add( HealthRecord.forMessage(
                            DomainID.systemId(),
                            HealthMessage.Email_ConnectFailure,
                            emailServer.getId().stringValue(),
                            "unable to connect" ) );
                }
                transport.close();
            }
            catch ( final Exception e )
            {
                records.add( HealthRecord.forMessage(
                        DomainID.systemId(),
                        HealthMessage.Email_ConnectFailure,
                        emailServer.getId().stringValue(),
                        e.getMessage() ) );
            }
        }

        return Collections.unmodifiableList( records );
    }
}
