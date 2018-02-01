package password.pwm.ws.server;

import com.google.gson.stream.MalformedJsonException;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Data;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.filter.RequestInitializationFilter;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public abstract class RestServlet extends HttpServlet{
    private static final AtomicLoopIntIncrementer REQUEST_COUNTER = new AtomicLoopIntIncrementer(Integer.MAX_VALUE);

    private static final PwmLogger LOGGER = PwmLogger.forClass(RestServlet.class);

    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException
    {
        final Instant startTime = Instant.now();

        RestResultBean restResultBean = RestResultBean.fromError(new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE), false);

        final PwmApplication pwmApplication;
        try {
            pwmApplication = ContextManager.getContextManager(req.getServletContext()).getPwmApplication();
        } catch (PwmUnrecoverableException e) {
            outputRestResultBean(restResultBean, req, resp);
            return;
        }

        final Locale locale;
        {
            final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
            locale = LocaleHelper.localeResolver(req.getLocale(), knownLocales);
        }

        final SessionLabel sessionLabel;
        try {
            sessionLabel = new SessionLabel(
                    "rest-" + REQUEST_COUNTER.next(),
                    null,
                    null,
                    RequestInitializationFilter.readUserIPAddress(req, pwmApplication.getConfig()),
                    RequestInitializationFilter.readUserHostname(req, pwmApplication.getConfig())
            );
        } catch (PwmUnrecoverableException e) {
            restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmApplication, locale, pwmApplication.getConfig(), pwmApplication.determineIfDetailErrorMsgShown());
            outputRestResultBean(restResultBean, req, resp);
            return;
        }
        LOGGER.trace(sessionLabel, "beginning rest service invocation");

        if (pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING) {
            outputRestResultBean(restResultBean, req, resp);
            return;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.ENABLE_EXTERNAL_WEBSERVICES)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "webservices are not enabled");
            restResultBean = RestResultBean.fromError(errorInformation, pwmApplication, locale, pwmApplication.getConfig(), pwmApplication.determineIfDetailErrorMsgShown());
            outputRestResultBean(restResultBean, req, resp);
            return;
        }

        try {
            final RestAuthentication restAuthentication = new RestAuthenticationProcessor(pwmApplication, sessionLabel, req).readRestAuthentication();
            LOGGER.debug(sessionLabel, "rest request authentication status: " + JsonUtil.serialize(restAuthentication));

            final RestRequest restRequest = RestRequest.forRequest(pwmApplication, restAuthentication, sessionLabel, req);

            RequestInitializationFilter.addStaticResponseHeaders( pwmApplication, resp );

            preCheck(restRequest);

            preCheckRequest(restRequest);

            restResultBean = invokeWebService(restRequest);
        } catch (PwmUnrecoverableException e) {
            restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmApplication, locale, pwmApplication.getConfig(), pwmApplication.determineIfDetailErrorMsgShown());
        } catch (Throwable e) {
            final String errorMsg = "internal error during rest service invocation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            restResultBean = RestResultBean.fromError(errorInformation, pwmApplication, locale, pwmApplication.getConfig(), pwmApplication.determineIfDetailErrorMsgShown());
            LOGGER.error(sessionLabel, errorInformation);
        }

        outputRestResultBean(restResultBean, req, resp);
        LOGGER.trace(sessionLabel, "completed rest invocation in " + TimeDuration.compactFromCurrent(startTime) + " success=" + !restResultBean.isError());
    }

    private RestResultBean invokeWebService(final RestRequest restRequest) throws IOException, PwmUnrecoverableException {
        final Method interestedMethod = discoverMethodForAction(this.getClass(), restRequest);

        if (interestedMethod != null) {
            interestedMethod.setAccessible(true);
            try {
                return (RestResultBean) interestedMethod.invoke(this, restRequest);
            } catch (InvocationTargetException e) {
                final Throwable rootException = e.getTargetException();
                if (rootException instanceof PwmUnrecoverableException) {
                    throw (PwmUnrecoverableException)rootException;
                }
                throw PwmUnrecoverableException.newException(PwmError.ERROR_UNKNOWN, e.getMessage());
            } catch (IllegalAccessException e) {
                throw PwmUnrecoverableException.newException(PwmError.ERROR_UNKNOWN, e.getMessage());
            }
        }
        return null;
    }

    @Data
    private static class MethodMatcher {
        boolean methodMatch;
        boolean contentMatch;
        boolean acceptMatch;
    }

    private Method discoverMethodForAction(final Class clazz, final RestRequest restRequest)
            throws PwmUnrecoverableException
    {
        final HttpMethod reqMethod = restRequest.getMethod();
        final HttpContentType reqContent = restRequest.readContentType();
        final HttpContentType reqAccept = restRequest.readAcceptType();

        final boolean careAboutContentType = reqMethod.isHasBody();

        final MethodMatcher anyMatch = new MethodMatcher();

        final Collection<Method> methods = JavaHelper.getAllMethodsForClass(clazz);
        for (Method method : methods) {
            final RestMethodHandler annotation = method.getAnnotation(RestMethodHandler.class);
            final MethodMatcher loopMatch = new MethodMatcher();

            if (annotation != null) {
                if (annotation.method().length == 0 || Arrays.asList(annotation.method()).contains(reqMethod)) {
                    loopMatch.setMethodMatch(true);
                    anyMatch.setMethodMatch(true);
                }

                if (!careAboutContentType || annotation.consumes().length == 0 || Arrays.asList(annotation.consumes()).contains(reqContent)) {
                    loopMatch.setContentMatch(true);
                    anyMatch.setContentMatch(true);
                }

                if (annotation.produces().length == 0 || Arrays.asList(annotation.produces()).contains(reqAccept)) {
                    loopMatch.setAcceptMatch(true);
                    anyMatch.setAcceptMatch(true);
                }

                if (loopMatch.isMethodMatch() && loopMatch.isContentMatch() && loopMatch.isAcceptMatch()) {
                    return method;
                }
            }
        }

        final String errorMsg;
        if (!anyMatch.isMethodMatch()) {
            errorMsg = "HTTP method invalid";
        } else if (reqAccept == null && !anyMatch.isAcceptMatch()) {
            errorMsg = HttpHeader.Accept.getHttpName() + " header is required";
        } else if (!anyMatch.isAcceptMatch()) {
            errorMsg = HttpHeader.Accept.getHttpName() + " header value does not match an available processor";
        } else if (reqContent == null && !anyMatch.isContentMatch()) {
            errorMsg = HttpHeader.Content_Type.getHttpName() + " header is required";
        } else if (!anyMatch.isContentMatch()) {
            errorMsg = HttpHeader.Content_Type.getHttpName() + " header value does not match an available processor";
        } else {
            errorMsg = "incorrect method, Content-Type header, or Accept header.";
        }

        throw PwmUnrecoverableException.newException(PwmError.ERROR_REST_INVOCATION_ERROR, errorMsg);
    }

    private void preCheck(final RestRequest restRequest)
            throws PwmUnrecoverableException
    {
        final RestWebServer classAnnotation = this.getClass().getDeclaredAnnotation(RestWebServer.class);
        if (classAnnotation == null) {
            throw PwmUnrecoverableException.newException(PwmError.ERROR_UNKNOWN, "class is missing " + RestWebServer.class.getSimpleName() + " annotation");
        }

        if (!restRequest.getRestAuthentication().getUsages().contains(classAnnotation.webService())) {
            throw PwmUnrecoverableException.newException(PwmError.ERROR_UNAUTHORIZED, "access to " + classAnnotation.webService() + " service is not permitted for this login");
        }

        if (classAnnotation.requireAuthentication()) {
            if (restRequest.getRestAuthentication().getType() == RestAuthenticationType.PUBLIC) {
                throw PwmUnrecoverableException.newException(PwmError.ERROR_UNAUTHORIZED, "this service requires authentication");
            }
        }

        if (restRequest.getMethod().isHasBody()) {
            if (restRequest.readContentType() == null) {
                final String message = restRequest.getMethod() + " method requires " + HttpHeader.Content_Type.getHttpName() + " header";
                throw PwmUnrecoverableException.newException(PwmError.ERROR_UNAUTHORIZED, message);
            }
        }
    }

    public abstract void preCheckRequest(RestRequest request) throws PwmUnrecoverableException;

    private void outputRestResultBean(
            final RestResultBean restResultBean,
            final HttpServletRequest request,
            final HttpServletResponse resp
    )
            throws IOException
    {
        final HttpContentType acceptType = RestRequest.readAcceptType(request);
        resp.setHeader(HttpHeader.Server.getHttpName(), PwmConstants.PWM_APP_NAME);

        if (acceptType != null) {
            switch (acceptType) {
                case json: {
                    resp.setHeader(HttpHeader.Content_Type.getHttpName(), HttpContentType.json.getHeaderValue());
                    try (PrintWriter pw = resp.getWriter()) {
                        pw.write(restResultBean.toJson());
                    }
                }
                break;

                case plain: {
                    resp.setHeader(HttpHeader.Content_Type.getHttpName(), HttpContentType.plain.getHeaderValue());
                    if (restResultBean.isError()) {
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, restResultBean.getErrorMessage());
                    } else {
                        if (restResultBean.getData() != null) {
                            try (PrintWriter pw = resp.getWriter()) {
                                pw.write(restResultBean.getData().toString());
                            }
                        }
                    }
                }
                break;

                default: {
                    final String msg = "unhandled " + HttpHeader.Accept.getHttpName() + " header value in request";
                    outputLastHopeError(msg, resp);
                }
            }
        } else {
            final String msg;
            if (StringUtil.isEmpty(request.getHeader(HttpHeader.Accept.getHttpName()))) {
                msg = "missing " + HttpHeader.Accept.getHttpName() + " header value in request";
            } else {
                msg = "unknown value for " + HttpHeader.Accept.getHttpName() + " header value in request";
            }
            outputLastHopeError(msg, resp);
        }
    }

    private static void outputLastHopeError(final String msg, final HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setHeader(HttpHeader.Content_Type.getHttpName(), HttpContentType.json.getHeaderValue());
        try (PrintWriter pw = response.getWriter()) {
            pw.write("Error: ");
            pw.write(msg);
            pw.write("\n");
        }
    }

    @Value
    public static class TargetUserIdentity {
        private RestRequest restRequest;
        private UserIdentity userIdentity;
        private boolean self;

        public ChaiProvider getChaiProvider() throws PwmUnrecoverableException {
            return restRequest.getChaiProvider(userIdentity.getLdapProfileID());
        }

        public ChaiUser getChaiUser() throws PwmUnrecoverableException {
            try {
                return getChaiProvider().getEntryFactory().newChaiUser(userIdentity.getUserDN());
            } catch (ChaiUnavailableException e) {
                throw PwmUnrecoverableException.fromChaiException(e);
            }
        }
    }

    public static <T> T deserializeJsonBody(final RestRequest restRequest, final Class<T> classOfT)
            throws IOException, PwmUnrecoverableException
    {
        try {
            final T jsonData = JsonUtil.deserialize(restRequest.readRequestBodyAsString(), classOfT);
            if (jsonData == null) {
                throw PwmUnrecoverableException.newException(PwmError.ERROR_REST_INVOCATION_ERROR, "missing json body");
            }
            return jsonData;
        } catch (Exception e) {
            if (e.getCause() instanceof MalformedJsonException) {
                throw PwmUnrecoverableException.newException(PwmError.ERROR_REST_INVOCATION_ERROR, "json parse error: " + e.getCause().getMessage());
            }
            throw PwmUnrecoverableException.newException(PwmError.ERROR_REST_INVOCATION_ERROR, "json parse error: " + e.getMessage());
        }
    }

    public static TargetUserIdentity resolveRequestedUsername(
            final RestRequest restRequest,
            final String username
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = restRequest.getPwmApplication();

        if (StringUtil.isEmpty(username)) {
            if (restRequest.getRestAuthentication().getType() == RestAuthenticationType.NAMED_SECRET) {
                throw PwmUnrecoverableException.newException(PwmError.ERROR_REST_INVOCATION_ERROR, "username field required");
            }
        } else {
            if (!restRequest.getRestAuthentication().isThirdPartyEnabled()) {
                throw PwmUnrecoverableException.newException(PwmError.ERROR_UNAUTHORIZED, "username specified in request, however third party permission is not granted to the authenticated login.");
            }
        }

        if (StringUtil.isEmpty(username)) {
            if (restRequest.getRestAuthentication().getType() == RestAuthenticationType.LDAP) {
                return new TargetUserIdentity(restRequest, restRequest.getRestAuthentication().getLdapIdentity(), true);
            }
        }

        final String ldapProfileID;
        final String effectiveUsername;
        if (username.contains("|")) {
            final int pipeIndex = username.indexOf("|");
            ldapProfileID = username.substring(0, pipeIndex);
            effectiveUsername = username.substring(pipeIndex + 1, username.length());
        } else {
            ldapProfileID = null;
            effectiveUsername = username;
        }

        try {
            final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
            final UserIdentity userIdentity = userSearchEngine.resolveUsername(effectiveUsername, null, ldapProfileID, restRequest.getSessionLabel());
            return new TargetUserIdentity(restRequest, userIdentity, false);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }
    }
}
