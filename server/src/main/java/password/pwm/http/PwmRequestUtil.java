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

package password.pwm.http;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.validator.routines.InetAddressValidator;
import password.pwm.Permission;
import password.pwm.PwmDomain;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.UserPermission;
import password.pwm.data.FileUploadItem;
import password.pwm.data.ImmutableByteArray;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PwmRequestUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmRequestUtil.class );

    public static Map<String, FileUploadItem> readFileUploads(
            final PwmRequest pwmRequest,
            final int maxFileSize,
            final int maxItems
    )
            throws PwmUnrecoverableException
    {
        final Map<String, FileUploadItem> returnObj = new LinkedHashMap<>();
        try
        {
            if ( ServletFileUpload.isMultipartContent( pwmRequest.getHttpServletRequest() ) )
            {
                final ServletFileUpload upload = new ServletFileUpload();
                final FileItemIterator iter = upload.getItemIterator( pwmRequest.getHttpServletRequest() );
                while ( iter.hasNext() && returnObj.size() < maxItems )
                {
                    final FileItemStream item = iter.next();
                    final InputStream inputStream = item.openStream();
                    final ImmutableByteArray fileContents = JavaHelper.copyToBytes( inputStream, maxFileSize + 1 );
                    if ( fileContents.size() > maxFileSize )
                    {
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "upload file size limit exceeded" );
                        LOGGER.error( pwmRequest, errorInformation );
                        pwmRequest.respondWithError( errorInformation );
                        return Collections.emptyMap();
                    }
                    final FileUploadItem fileUploadItem = new FileUploadItem(
                            item.getName(),
                            item.getContentType(),
                            fileContents
                    );
                    returnObj.put( item.getFieldName(), fileUploadItem );
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error reading file upload: " + e.getMessage() );
        }
        return Collections.unmodifiableMap( returnObj );
    }

    public static Optional<String> readUserHostname(
            final HttpServletRequest request,
            final AppConfig config
    )
    {
        if ( config != null && !config.readSettingAsBoolean( PwmSetting.REVERSE_DNS_ENABLE ) )
        {
            return Optional.empty();
        }

        final Optional<String> userIPAddress = readUserNetworkAddress( request, config );
        if ( userIPAddress.isPresent() )
        {
            try
            {
                return Optional.of( InetAddress.getByName( userIPAddress.get() ).getCanonicalHostName() );
            }
            catch ( final UnknownHostException e )
            {
                LOGGER.trace( () -> "unknown host while trying to compute hostname for src request: " + e.getMessage() );
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the IP address of the user.  If there is an X-Forwarded-For header in the request, that address will
     * be used.  Otherwise, the source address of the request is used.
     *
     * @param request the http request object
     * @param config the application configuration
     * @return String containing the textual representation of the source IP address, or null if the request is invalid.
     */
    public static Optional<String> readUserNetworkAddress(
            final HttpServletRequest request,
            final AppConfig config
    )
    {
        final List<String> candidateAddresses = new ArrayList<>();

        final boolean useXForwardedFor = config != null && config.readSettingAsBoolean( PwmSetting.USE_X_FORWARDED_FOR_HEADER );
        if ( useXForwardedFor )
        {
            final String xForwardedForValue = request.getHeader( HttpHeader.XForwardedFor.getHttpName() );
            if ( StringUtil.notEmpty( xForwardedForValue ) )
            {
                Collections.addAll( candidateAddresses, xForwardedForValue.split( "," ) );
            }
        }

        final String sourceIP = request.getRemoteAddr();
        if ( StringUtil.notEmpty( sourceIP ) )
        {
            candidateAddresses.add( sourceIP );
        }

        for ( final String candidateAddress : candidateAddresses )
        {
            final String trimAddr = candidateAddress.trim();
            if ( InetAddressValidator.getInstance().isValid( trimAddr ) )
            {
                return Optional.of( trimAddr );
            }
            else
            {
                LOGGER.warn( () -> "discarding bogus source network address '" + trimAddr + "'" );
            }
        }

        return Optional.empty();
    }

    public static boolean checkPermission(
            final PwmRequest pwmRequest,
            final Permission permission
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        if ( !pwmRequest.isAuthenticated() )
        {
            LOGGER.trace( pwmRequest, () -> "user is not authenticated, returning false for permission check" );
            return false;
        }

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        Permission.PermissionStatus status = pwmRequest.getPwmSession().getUserSessionDataCacheBean().getPermission( permission );
        if ( status == Permission.PermissionStatus.UNCHECKED )
        {
            LOGGER.debug( pwmRequest, () -> "checking permission " + permission.toString() + " for user "
                    + pwmSession.getUserInfo().getUserIdentity().toDisplayString() );

            if ( permission == Permission.PWMADMIN && !pwmDomain.getConfig().isAdministrativeDomain() )
            {
                status = Permission.PermissionStatus.DENIED;
            }
            else
            {
                final PwmSetting setting = permission.getPwmSetting();
                final List<UserPermission> userPermission = pwmDomain.getConfig().readSettingAsUserPermission( setting );
                final boolean result = UserPermissionUtility.testUserPermission( pwmDomain, pwmRequest.getLabel(), pwmSession.getUserInfo().getUserIdentity(), userPermission );
                status = result ? Permission.PermissionStatus.GRANTED : Permission.PermissionStatus.DENIED;
            }

            pwmSession.getUserSessionDataCacheBean().setPermission( permission, status );

            {
                final String debugName = pwmSession.getUserInfo().getUserIdentity().toDisplayString();
                final Permission.PermissionStatus finalStatus = status;
                LOGGER.debug( pwmRequest.getLabel(), () -> "permission " + permission + " for user " + debugName + " is " + finalStatus, TimeDuration.fromCurrent( startTime ) );
            }
        }

        return status == Permission.PermissionStatus.GRANTED;
    }

}
