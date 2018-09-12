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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.ShortcutItem;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ShortcutsBean;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet(
        name = "ShortcutServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/shortcuts",
                PwmConstants.URL_PREFIX_PRIVATE + "/Shortcuts",
        }
)
public class ShortcutServlet extends AbstractPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ShortcutServlet.class );

    public enum ShortcutAction implements AbstractPwmServlet.ProcessAction
    {
        selectShortcut,;

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( HttpMethod.GET );
        }
    }

    protected ShortcutAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            return ShortcutAction.valueOf( request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
        }
        catch ( IllegalArgumentException e )
        {
            return null;
        }
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.SHORTCUT_ENABLE ) )
        {
            pwmRequest.respondWithError( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
            return;
        }

        final ShortcutsBean shortcutsBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ShortcutsBean.class );
        if ( shortcutsBean.getVisibleItems() == null )
        {
            LOGGER.debug( pwmSession, "building visible shortcut list for user" );
            final Map<String, ShortcutItem> visibleItems = figureVisibleShortcuts( pwmRequest );
            shortcutsBean.setVisibleItems( visibleItems );
        }
        else
        {
            LOGGER.trace( pwmSession, "using cashed shortcut values" );
        }

        final ShortcutAction action = readProcessAction( pwmRequest );
        if ( action != null )
        {
            pwmRequest.validatePwmFormID();
            switch ( action )
            {
                case selectShortcut:
                    handleUserSelection( pwmRequest, shortcutsBean );
                    return;

                default:
                    JavaHelper.unhandledSwitchStatement( action );
            }
        }

        forwardToJsp( pwmRequest, shortcutsBean );
    }

    private void forwardToJsp( final PwmRequest pwmRequest, final ShortcutsBean shortcutsBean )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final ArrayList<ShortcutItem> shortcutItems = new ArrayList<>();
        shortcutItems.addAll( shortcutsBean.getVisibleItems().values() );
        pwmRequest.setAttribute( PwmRequestAttribute.ShortcutItems, shortcutItems );
        pwmRequest.forwardToJsp( JspUrl.SHORTCUT );
    }

    /**
     * Loop through each configured shortcut setting to determine if the shortcut is is able to the user pwmSession.
     */
    private static Map<String, ShortcutItem> figureVisibleShortcuts(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Collection<String> configValues = pwmRequest.getConfig().readSettingAsLocalizedStringArray( PwmSetting.SHORTCUT_ITEMS, pwmRequest.getLocale() );

        final Set<String> labelsFromHeader = new HashSet<>();
        {
            final Map<String, List<String>> headerValueMap = pwmRequest.readHeaderValuesMap();
            final List<String> interestedHeaderNames = pwmRequest.getConfig().readSettingAsStringArray( PwmSetting.SHORTCUT_HEADER_NAMES );

            for ( final Map.Entry<String, List<String>> entry : headerValueMap.entrySet() )
            {
                final String headerName = entry.getKey();
                if ( interestedHeaderNames.contains( headerName ) )
                {
                    for ( final String loopValues : entry.getValue() )
                    {
                        labelsFromHeader.addAll( StringHelper.tokenizeString( loopValues, "," ) );
                    }
                }
            }
        }

        final List<ShortcutItem> configuredItems = new ArrayList<>();
        for ( final String loopStr : configValues )
        {
            final ShortcutItem item = ShortcutItem.parsePwmConfigInput( loopStr );
            configuredItems.add( item );
        }

        final Map<String, ShortcutItem> visibleItems = new LinkedHashMap<>();

        if ( !labelsFromHeader.isEmpty() )
        {
            LOGGER.trace( "detected the following labels from headers: " + StringHelper.stringCollectionToString( labelsFromHeader, "," ) );
            visibleItems.keySet().retainAll( labelsFromHeader );
        }
        else
        {
            for ( final ShortcutItem item : configuredItems )
            {
                final boolean queryMatch = LdapPermissionTester.testQueryMatch(
                        pwmRequest.getPwmApplication(),
                        pwmRequest.getSessionLabel(),
                        pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                        item.getLdapQuery()
                );

                if ( queryMatch )
                {
                    visibleItems.put( item.getLabel(), item );
                }
            }
        }

        return visibleItems;
    }

    private void handleUserSelection(
            final PwmRequest pwmRequest,
            final ShortcutsBean shortcutsBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final String link = pwmRequest.readParameterAsString( "link" );
        final Map<String, ShortcutItem> visibleItems = shortcutsBean.getVisibleItems();

        if ( link != null && visibleItems.keySet().contains( link ) )
        {
            final ShortcutItem item = visibleItems.get( link );

            pwmApplication.getStatisticsManager().incrementValue( Statistic.SHORTCUTS_SELECTED );
            LOGGER.trace( pwmSession, "shortcut link selected: " + link + ", setting link for 'forwardURL' to " + item.getShortcutURI() );
            pwmSession.getSessionStateBean().setForwardURL( item.getShortcutURI().toString() );

            pwmRequest.sendRedirectToContinue();
            return;
        }

        LOGGER.error( pwmSession, "unknown/unexpected link requested to " + link );
        pwmRequest.forwardToJsp( JspUrl.SHORTCUT );
    }
}
