/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

package password.pwm.servlet;

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.GuestRegistrationServletBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.*;
import password.pwm.util.stats.Statistic;
import password.pwm.wordlist.SeedlistManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Servlet for creating new guest users (helpdesk/admin registration)
 *
 * @author Jason D. Rivard, Menno Pieters
 */
public class GuestRegistrationServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(GuestRegistrationServlet.class);

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException, NumberFormatException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final Locale locale = Locale.getDefault();
        String durationString = null;
        Properties notifyAttrs = new Properties();

        if (!ssBean.isAuthenticated()) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }
        
        if (!Permission.checkPermission(Permission.GUEST_REGISTRATION, pwmSession)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }
        
        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 255);
        final IntruderManager intruderMgr = pwmSession.getContextManager().getIntruderManager();
        final Configuration config = pwmSession.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) {
            ssBean.setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final GuestRegistrationServletBean grBean = pwmSession.getGuestRegistrationServletBean();

        if (actionParam != null && actionParam.equalsIgnoreCase("create")) {
            final Map<String, FormConfiguration> creationParams = grBean.getCreationParams();
            final Properties createAttrs = new Properties();
            final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);

            Validator.validatePwmFormID(req);

            //read the values from the request
            try {
                Validator.updateParamValues(pwmSession, req, creationParams);
            } catch (PwmDataValidationException e) {
                ssBean.setSessionError(e.getErrorInformation());
                this.forwardToJSP(req, resp);
                return;
            }

            // see if the values meet form requirements.
            try {
                Validator.validateParmValuesMeetRequirements(creationParams, pwmSession);
                if (expirationAttribute != null && expirationAttribute.length() > 0) {
	                durationString = creationParams.get("__accountDuration__").getValue();
        	        final Integer maxDuration = Integer.parseInt(config.readSettingAsString(PwmSetting.GUEST_MAX_VALID_DAYS));
	                Validator.validateNumericString(durationString, 0, maxDuration, pwmSession);
                }
            } catch (NumberFormatException e) {
                ssBean.setSessionError(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null));
                intruderMgr.addBadAddressAttempt(pwmSession);
                this.forwardToJSP(req, resp);
                return;
            } catch (PwmDataValidationException e) {
                ssBean.setSessionError(e.getErrorInformation());
                intruderMgr.addBadAddressAttempt(pwmSession);
                this.forwardToJSP(req, resp);
                return;
            }

            // verify naming attribute is present
            {
                final FormConfiguration formConfig = creationParams.get(config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE));
                if (formConfig == null || formConfig.getValue() == null || formConfig.getValue().length() < 1) {
                    ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_NAMING_ATTR));
                    intruderMgr.addBadAddressAttempt(pwmSession);
                    this.forwardToJSP(req, resp);
                    return;
                }
            }

            // check unique fields against ldap
            for (final String attr : config.readStringArraySetting(PwmSetting.GUEST_UNIQUE_ATTRIBUTES)) {
                final FormConfiguration paramConfig = creationParams.get(attr);
                try {
                    validateAttributeUniqueness(pwmSession, paramConfig, grBean.getCreateUserDN());
                } catch (PwmDataValidationException e) {
                    ssBean.setSessionError(e.getErrorInformation());
                    intruderMgr.addBadAddressAttempt(pwmSession);
                    this.forwardToJSP(req, resp);
                    return;
                }
            }

            //create user
            try {
                final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
                final String namingValue = creationParams.get(config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE)).getValue();

                final StringBuilder dn = new StringBuilder();
                dn.append(config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE)).append("=");
                dn.append(namingValue);
                dn.append(",");
                dn.append(config.readSettingAsString(PwmSetting.GUEST_CONTEXT));

                for (final String key : creationParams.keySet()) {
                	if (!key.equalsIgnoreCase("__accountDuration__")) { 
                    	final FormConfiguration formConfig = creationParams.get(key);
                    	createAttrs.put(formConfig.getAttributeName(), formConfig.getValue());
                    	notifyAttrs.put(formConfig.getAttributeName(), formConfig.getValue());
                	}
                }
                // Write creator DN
                createAttrs.put(config.readSettingAsString(PwmSetting.GUEST_ADMIN_ATTRIBUTE), pwmSession.getUserInfoBean().getUserDN());
                
                List<String> createObjectClasses = config.readStringArraySetting(PwmSetting.DEFAULT_OBJECT_CLASSES);
                if (createObjectClasses == null || createObjectClasses.isEmpty()) {
                	createObjectClasses = new ArrayList<String>();
                	createObjectClasses.add(ChaiConstant.OBJECTCLASS_BASE_LDAP_USER);
                }
                final Set<String> createObjectClassesSet = new HashSet<String>(createObjectClasses);
                provider.createEntry(dn.toString(), createObjectClassesSet, createAttrs);

                grBean.setCreateUserDN(dn.toString());

                LOGGER.info(pwmSession, "created guest user object: " + dn.toString());
            } catch (ChaiOperationException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error creating guest user: " + e.getMessage());
                ssBean.setSessionError(info);
                LOGGER.warn(pwmSession, info);
                this.forwardToJSP(req, resp);
                return;
            } catch (NullPointerException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error creating guest user (missing cn/sn?): " + e.getMessage());
                ssBean.setSessionError(info);
                LOGGER.warn(pwmSession, info);
                this.forwardToJSP(req, resp);
                return;
            }

            try {
                final ChaiUser theGuest = ChaiFactory.createChaiUser(grBean.getCreateUserDN(), pwmSession.getSessionManager().getChaiProvider());
				GregorianCalendar cal = null;
				String expirationDate = null;
				
				// If an expiration date attribute is available, calculate the expiration date and convert it to
				//   Greenwich Mean Time.
				if (expirationAttribute != null && expirationAttribute.length() > 0 && durationString != null) {
					cal = new GregorianCalendar(locale);
					cal.set(Calendar.HOUR_OF_DAY,0);
					cal.set(Calendar.MINUTE,0);
					cal.set(Calendar.SECOND,0);
					cal.set(Calendar.MILLISECOND,0);
					cal.add(Calendar.DAY_OF_MONTH,Integer.parseInt(durationString));
					TimeZone tz = TimeZone.getTimeZone("GMT");
					cal.setTimeZone(tz);
					SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
					expirationDate = fmt.format(cal.getTime());
					SimpleDateFormat nfmt = new SimpleDateFormat();
					notifyAttrs.put(expirationAttribute, nfmt.format(cal.getTime()));
					LOGGER.trace("expiration: "+expirationAttribute+"="+expirationDate+" ("+nfmt.format(cal.getTime())+")");
				}

                // write out configured attributes.
                LOGGER.debug(pwmSession, "writing guest.writeAttributes to user " + theGuest.getEntryDN());
                final List<String> configValues = config.readStringArraySetting(PwmSetting.GUEST_WRITE_ATTRIBUTES);
                final Map<String, String> configNameValuePairs = Configuration.convertStringListToNameValuePair(configValues,"=");
                if (expirationDate != null) {
                	configNameValuePairs.put(expirationAttribute, expirationDate);
                }
                Helper.writeMapToEdir(pwmSession, theGuest, configNameValuePairs);
                for (final String key : configNameValuePairs.keySet()) {
                	notifyAttrs.put(key, configNameValuePairs.get(key));
                }

                final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(config, locale, theGuest, null);
                final SeedlistManager seedlistManager = pwmSession.getContextManager().getSeedlistManager();
                final String newPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, passwordPolicy, seedlistManager, pwmSession.getContextManager());
                theGuest.setPassword(newPassword);
				notifyAttrs.put("password", newPassword);

                //AuthenticationFilter.authUserWithUnknownPassword(theGuest, pwmSession, req);
            } catch (ImpossiblePasswordPolicyException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected ImpossiblePasswordPolicyException error while creating guest user");
                LOGGER.warn(pwmSession, info, e);
                ssBean.setSessionError(info);
                this.forwardToJSP(req, resp);
                return;
            } catch (PwmOperationalException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error writing to ldap: " + e.getMessage());
                LOGGER.warn(pwmSession, info, e);
                ssBean.setSessionError(info);
                ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
                return;
            } catch (ChaiOperationException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error writing to ldap: " + e.getMessage());
                LOGGER.warn(pwmSession, info, e);
                ssBean.setSessionError(info);
                ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
                return;
            }

            //everything good so forward to confirmation page.
            this.sendNewGuestEmailConfirmation(pwmSession, notifyAttrs);
            ssBean.setSessionSuccess(Message.SUCCESS_CREATE_GUEST, null);

            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.GUESTS);
            ServletHelper.forwardToSuccessPage(req, resp, this.getServletContext());
            return;
        }
        this.forwardToJSP(req, resp);
    }

    public static void validateAttributeUniqueness(
            final PwmSession pwmSession,
            final FormConfiguration paramConfig,
            final String userDN
    )
            throws PwmDataValidationException, ChaiUnavailableException
    {
        try {
            final ChaiProvider provider = pwmSession.getContextManager().getProxyChaiProvider();

            final Map<String, String> filterClauses = new HashMap<String, String>();
            filterClauses.put(ChaiConstant.ATTR_LDAP_OBJECTCLASS, ChaiConstant.OBJECTCLASS_BASE_LDAP_USER);
            filterClauses.put(paramConfig.getAttributeName(), paramConfig.getValue());

            final SearchHelper searchHelper = new SearchHelper();
            searchHelper.setFilterAnd(filterClauses);

            final Set<String> resultDNs = new HashSet<String>(provider.search(pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT), searchHelper).keySet());

            // remove the user DN from the result set.
            resultDNs.remove(userDN);

            if (resultDNs.size() > 0) {
                final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null, paramConfig.getLabel());
                throw new PwmDataValidationException(error);
            }
        } catch (ChaiOperationException e) {
            LOGGER.debug(e);
        }
    }

    private void sendNewGuestEmailConfirmation(final PwmSession pwmSession, final Properties attrs) {
        final ContextManager theManager = pwmSession.getContextManager();
        final Configuration config = pwmSession.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readLocalizedStringSetting(PwmSetting.EMAIL_GUEST_FROM,locale);
        final String subject = config.readLocalizedStringSetting(PwmSetting.EMAIL_GUEST_SUBJECT,locale);
        final String plainBody = Helper.replaceAllPatterns(config.readLocalizedStringSetting(PwmSetting.EMAIL_GUEST_BODY,locale), attrs);
        final String htmlBody = Helper.replaceAllPatterns(config.readLocalizedStringSetting(PwmSetting.EMAIL_GUEST_BODY_HTML,locale), attrs);

        final String toAddress = attrs.getProperty(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));
        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send new guest user email: no email configured");
            return;
        }

        theManager.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_GUEST_REGISTRATION).forward(req, resp);
    }
}

