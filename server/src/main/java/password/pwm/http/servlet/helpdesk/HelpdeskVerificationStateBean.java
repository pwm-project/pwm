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

package password.pwm.http.servlet.helpdesk;

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

class HelpdeskVerificationStateBean implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskVerificationStateBean.class );
    public static final String PARAMETER_VERIFICATION_STATE_KEY = "verificationState";

    private final UserIdentity actor;
    private final List<HelpdeskValidationRecord> records = new ArrayList<>();

    private transient TimeDuration maximumAge;



    private HelpdeskVerificationStateBean( final UserIdentity actor )
    {
        this.actor = actor;
    }

    public UserIdentity getActor( )
    {
        return actor;
    }

    public List<HelpdeskValidationRecord> getRecords( )
    {
        return records;
    }

    public void addRecord( final UserIdentity identity, final IdentityVerificationMethod method )
    {
        purgeOldRecords();

        final HelpdeskValidationRecord record = getRecord( identity, method );
        if ( record != null )
        {
            records.remove( record );
        }
        records.add( new HelpdeskValidationRecord( Instant.now(), identity, method ) );
    }

    public boolean hasRecord( final UserIdentity identity, final IdentityVerificationMethod method )
    {
        purgeOldRecords();
        return getRecord( identity, method ) != null;
    }

    private HelpdeskValidationRecord getRecord( final UserIdentity identity, final IdentityVerificationMethod method )
    {
        for ( final HelpdeskValidationRecord record : records )
        {
            if ( record.getIdentity().equals( identity ) && ( method == null || record.getMethod() == method ) )
            {
                return record;
            }
        }
        return null;
    }


    void purgeOldRecords( )
    {
        for ( final Iterator<HelpdeskValidationRecord> iterator = records.iterator(); iterator.hasNext(); )
        {
            final HelpdeskValidationRecord record = iterator.next();
            final Instant timestamp = record.getTimestamp();
            final TimeDuration age = TimeDuration.fromCurrent( timestamp );
            if ( age.isLongerThan( maximumAge ) )
            {
                iterator.remove();
            }
        }
    }

    List<ViewableValidationRecord> asViewableValidationRecords(
            final PwmApplication pwmApplication,
            final Locale locale
    )
            throws ChaiOperationException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final Map<Instant, ViewableValidationRecord> returnRecords = new TreeMap<>();
        for ( final HelpdeskValidationRecord record : records )
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, SessionLabel.SYSTEM_LABEL, record.getIdentity(), PwmConstants.DEFAULT_LOCALE );
            final String username = userInfo.getUsername();
            final String profile = pwmApplication.getConfig().getLdapProfiles().get( record.getIdentity().getLdapProfileID() ).getDisplayName( locale );
            final String method = record.getMethod().getLabel( pwmApplication.getConfig(), locale );
            returnRecords.put( record.getTimestamp(), new ViewableValidationRecord( record.getTimestamp(), profile, username, method ) );
        }
        return Collections.unmodifiableList( new ArrayList<>( returnRecords.values() ) );
    }

    @Value
    static class ViewableValidationRecord implements Serializable
    {
        private Instant timestamp;
        private String profile;
        private String username;
        private String method;
    }

    @Value
    static class HelpdeskValidationRecord implements Serializable
    {
        private Instant timestamp;
        private UserIdentity identity;
        private IdentityVerificationMethod method;
    }

    String toClientString( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        return pwmApplication.getSecureService().encryptObjectToString( this );
    }

    static HelpdeskVerificationStateBean fromClientString(
            final PwmRequest pwmRequest,
            final String rawValue
    )
            throws PwmUnrecoverableException
    {
        final int maxAgeSeconds = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.HELPDESK_VERIFICATION_TIMEOUT_SECONDS ) );
        final TimeDuration maxAge = TimeDuration.of( maxAgeSeconds, TimeDuration.Unit.SECONDS );
        final UserIdentity actor = pwmRequest.getUserInfoIfLoggedIn();

        HelpdeskVerificationStateBean state = null;
        if ( rawValue != null && !rawValue.isEmpty() )
        {
            state = pwmRequest.getPwmApplication().getSecureService().decryptObject( rawValue, HelpdeskVerificationStateBean.class );
            if ( !state.getActor().equals( actor ) )
            {
                state = null;
            }
        }

        state = state != null ? state : new HelpdeskVerificationStateBean( actor );
        state.maximumAge = maxAge;
        state.purgeOldRecords();

        LOGGER.debug( pwmRequest, "read current state: " + JsonUtil.serialize( state ) );

        return state;
    }
}


