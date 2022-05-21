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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.ShortcutItem;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ShortcutsBean;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@WebServlet(
        name = "ShortcutServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/shortcuts",
                PwmConstants.URL_PREFIX_PRIVATE + "/Shortcuts",
        }
)
public class ShortcutServlet extends ControlledPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ShortcutServlet.class );

    public enum ShortcutAction implements AbstractPwmServlet.ProcessAction
    {
        selectShortcut,;

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( HttpMethod.GET );
        }
    }

    @Override
    protected Optional<ShortcutAction> readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        return JavaHelper.readEnumFromString( ShortcutAction.class, request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass()
    {
        return ShortcutAction.class;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        forwardToJsp( pwmRequest, getBean( pwmRequest ) );

    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        if ( !pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.SHORTCUT_ENABLE ) )
        {
            pwmRequest.respondWithError( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
            return ProcessStatus.Halt;
        }

        final ShortcutsBean shortcutsBean = getBean( pwmRequest );
        if ( shortcutsBean.getVisibleItems() == null )
        {
            final Map<String, ShortcutItem> visibleItems = figureVisibleShortcuts( pwmRequest );
            shortcutsBean.setVisibleItems( visibleItems );
        }
        else
        {
            LOGGER.trace( pwmRequest, () -> "using cashed shortcut values" );
        }

        return ProcessStatus.Continue;
    }

    public ShortcutsBean getBean( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        return pwmDomain.getSessionStateService().getBean( pwmRequest, ShortcutsBean.class );

    }

    @ActionHandler( action = "selectShortcut" )
    public ProcessStatus processSelectShortcutRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final ShortcutsBean shortcutsBean = getBean( pwmRequest );
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String link = pwmRequest.readParameterAsString( "link" );
        final Map<String, ShortcutItem> visibleItems = shortcutsBean.getVisibleItems();

        if ( link != null && visibleItems.containsKey( link ) )
        {
            final ShortcutItem item = visibleItems.get( link );

            StatisticsClient.incrementStat( pwmRequest, Statistic.SHORTCUTS_SELECTED );
            LOGGER.trace( pwmRequest, () -> "shortcut link selected: " + link + ", setting link for 'forwardURL' to " + item.getShortcutURI() );
            pwmSession.getSessionStateBean().setForwardURL( item.getShortcutURI().toString() );

            pwmRequest.getPwmResponse().sendRedirectToContinue();
            return ProcessStatus.Halt;
        }

        LOGGER.error( pwmRequest, () -> "unknown/unexpected link requested to " + link );
        return ProcessStatus.Continue;
    }

    private void forwardToJsp( final PwmRequest pwmRequest, final ShortcutsBean shortcutsBean )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final ArrayList<ShortcutItem> shortcutItems = new ArrayList<>( shortcutsBean.getVisibleItems().values() );
        pwmRequest.setAttribute( PwmRequestAttribute.ShortcutItems, shortcutItems );
        pwmRequest.forwardToJsp( JspUrl.SHORTCUT );
    }

    /**
     * Loop through each configured shortcut setting to determine if the shortcut is able to the user pwmSession.
     */
    private static Map<String, ShortcutItem> figureVisibleShortcuts(
            final PwmRequest pwmRequest
    )
    {
        LOGGER.debug( pwmRequest, () -> "building visible shortcut list for user" );

        final List<String> interestedHeaderNames = pwmRequest.getDomainConfig().readSettingAsStringArray( PwmSetting.SHORTCUT_HEADER_NAMES );
        final Set<String> labelsFromHeader = pwmRequest.readHeaderValuesMap().entrySet().stream()
                .filter( entry -> StringUtil.caseIgnoreContains(  interestedHeaderNames, entry.getKey() ) )
                .flatMap( stringListEntry -> stringListEntry.getValue().stream() )
                .flatMap( value -> StringUtil.tokenizeString( value, "," ).stream() )
                .collect( Collectors.toUnmodifiableSet() );

        if ( !interestedHeaderNames.isEmpty() )
        {
            LOGGER.trace( pwmRequest, () -> "examined headers '" + StringUtil.collectionToString( interestedHeaderNames )
                    + "' and found these values: '" + StringUtil.collectionToString( labelsFromHeader ) + "'" );
        }

        final Collection<String> configValues = pwmRequest.getDomainConfig().readSettingAsLocalizedStringArray( PwmSetting.SHORTCUT_ITEMS, pwmRequest.getLocale() );
        final List<ShortcutItem> configuredItems = configValues.stream()
                .map( ShortcutItem::parsePwmConfigInput )
                .collect( Collectors.toUnmodifiableList() );


        final Map<String, ShortcutItem> visibleItems = Collections.unmodifiableMap( configuredItems.stream()
                .filter( item -> checkItemMatch( pwmRequest, labelsFromHeader, item ) )
                .collect( CollectionUtil.collectorToLinkedMap(
                        ShortcutItem::getLabel,
                        Function.identity() ) ) );

        LOGGER.debug( pwmRequest, () -> "built visible shortcut list for user: '" + StringUtil.collectionToString( visibleItems.keySet() ) + "'" );

        return visibleItems;
    }

    private static boolean checkItemMatch(
            final PwmRequest pwmRequest,
            final Set<String> labelsFromHeader,
            final ShortcutItem item
    )
    {
        if ( StringUtil.caseIgnoreContains( labelsFromHeader, item.getLabel() ) )
        {
            LOGGER.trace( () -> "adding the shortcut item '" + item.getLabel() + "' due to presence of configured headers in request" );
            return true;
        }

        final UserIdentity userIdentity = pwmRequest.getPwmSession().getUserInfo().getUserIdentity();

        final UserPermission userPermission = UserPermission.builder()
                .type( UserPermissionType.ldapQuery )
                .ldapQuery( item.getLdapQuery() )
                .ldapBase( userIdentity.getUserDN() )
                .build();

        try
        {
            final boolean match = UserPermissionUtility.testUserPermission(
                    pwmRequest.getPwmRequestContext(),
                    pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                    userPermission
            );

            if ( match )
            {
                LOGGER.trace( pwmRequest, () -> "adding the shortcut item '" + item.getLabel() + "' due to ldap query match" );
            }

            return match;
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.trace( pwmRequest, () -> "error during ldap user permission test of shortcut label '" + item.getLabel() + "', error: " + e.getMessage() );
        }

        return false;
    }

}
