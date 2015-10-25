package password.pwm.svc;

import password.pwm.PwmApplication;
import password.pwm.VersionChecker;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.http.servlet.resource.ResourceServletService;
import password.pwm.ldap.LdapConnectionService;
import password.pwm.svc.cache.CacheService;
import password.pwm.svc.event.AuditService;
import password.pwm.svc.intruder.IntruderManager;
import password.pwm.svc.report.ReportService;
import password.pwm.svc.sessiontrack.SessionTrackService;
import password.pwm.svc.shorturl.UrlShortenerService;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.wordlist.SeedlistManager;
import password.pwm.svc.wordlist.SharedHistoryManager;
import password.pwm.svc.wordlist.WordlistManager;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.DatabaseAccessorImpl;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.OtpService;
import password.pwm.util.queue.EmailQueueManager;
import password.pwm.util.queue.SmsQueueManager;
import password.pwm.util.secure.SecureService;

import java.util.*;

public class PwmServiceManager {

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmServiceManager.class);

    private static final List<Class<? extends PwmService>> PWM_SERVICE_CLASSES  = Collections.unmodifiableList(Arrays.asList(
            SecureService.class,
            LdapConnectionService.class,
            DatabaseAccessorImpl.class,
            SharedHistoryManager.class,
            HealthMonitor.class,
            AuditService.class,
            StatisticsManager.class,
            WordlistManager.class,
            SeedlistManager.class,
            EmailQueueManager.class,
            SmsQueueManager.class,
            UrlShortenerService.class,
            TokenService.class,
            VersionChecker.class,
            IntruderManager.class,
            ReportService.class,
            CrService.class,
            OtpService.class,
            CacheService.class,
            ResourceServletService.class,
            SessionTrackService.class
    ));

    private final PwmApplication pwmApplication;
    private final Map<Class<? extends PwmService>,PwmService> pwmServices = new HashMap<>();
    private boolean initialized;

    public PwmServiceManager(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public PwmService getService(final Class<? extends PwmService> serviceClass) {
        return pwmServices.get(serviceClass);
    }


    public void initAllServices()
            throws PwmUnrecoverableException
    {
        for (final Class<? extends PwmService> serviceClass : PWM_SERVICE_CLASSES) {
            final PwmService newServiceInstance = initService(serviceClass);
            pwmServices.put(serviceClass,newServiceInstance);
        }
        initialized = true;
    }

    private PwmService initService(final Class<? extends PwmService> serviceClass)
            throws PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final PwmService newServiceInstance;
        final String serviceName = serviceClass.getName();
        try {
            final Object newInstance = serviceClass.newInstance();
            newServiceInstance = (PwmService)newInstance;
        } catch (Exception e) {
            final String errorMsg = "unexpected error instantiating service class '" + serviceName + "', error: " + e.toString();
            LOGGER.fatal(errorMsg,e);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR,errorMsg));
        }

        try {
            LOGGER.debug("initializing service " + serviceName);
            newServiceInstance.init(pwmApplication);
            final TimeDuration startupDuration = TimeDuration.fromCurrent(startTime);
            LOGGER.debug("completed initialization of service " + serviceName + " in " + startupDuration.asCompactString() + ", status=" + newServiceInstance.status());
        } catch (PwmException e) {
            LOGGER.warn("error instantiating service class '" + serviceName + "', service will remain unavailable, error: " + e.getMessage());
        } catch (Exception e) {
            String errorMsg = "unexpected error instantiating service class '" + serviceName + "', cannot load, error: " + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += ", cause: " + e.getCause();
            }
            LOGGER.fatal(errorMsg);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR,errorMsg));
        }
        return newServiceInstance;
    }


    public void shutdownAllServices()
    {
        if (!initialized) {
            return;
        }

        final List<Class<? extends PwmService>> reverseServiceList = new ArrayList<>(PWM_SERVICE_CLASSES);
        Collections.reverse(reverseServiceList);
        for (final Class<? extends PwmService> serviceClass : reverseServiceList) {
            if (pwmServices.containsKey(serviceClass)) {
                shutDownService(serviceClass);
            }
        }
        initialized = false;
    }

    private void shutDownService(final Class<? extends PwmService> serviceClass)
    {
        LOGGER.trace("closing service " + serviceClass.getName());
        final PwmService loopService = pwmServices.get(serviceClass);
        LOGGER.trace("successfully closed service " + serviceClass.getName());
        try {
            loopService.close();
        } catch (Exception e) {
            LOGGER.error("error closing " + loopService.getClass().getSimpleName() + ": " + e.getMessage(),e);
        }
    }

    public List<PwmService> getPwmServices() {
        return Collections.unmodifiableList(new ArrayList<PwmService>(this.pwmServices.values()));
    }

}
