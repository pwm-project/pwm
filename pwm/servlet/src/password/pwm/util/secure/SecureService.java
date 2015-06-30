package password.pwm.util.secure;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmService;
import password.pwm.config.Configuration;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.util.Helper;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class SecureService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SecureService.class);

    private PwmSecurityKey pwmSecurityKey;
    private PwmBlockAlgorithm defaultBlockAlgorithm;
    private PwmHashAlgorithm defaultHashAlorithm;

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        final Configuration config = pwmApplication.getConfig();
        pwmSecurityKey = config.getSecurityKey();
        {
            final String defaultBlockAlgString = config.readAppProperty(AppProperty.SECURITY_DEFAULT_EPHEMERAL_BLOCK_ALG);
            defaultBlockAlgorithm = Helper.readEnumFromString(PwmBlockAlgorithm.class, PwmBlockAlgorithm.AES, defaultBlockAlgString);
            LOGGER.debug("using default ephemeral block algorithm: "+ defaultBlockAlgorithm.getLabel());
        }
        {
            final String defaultHashAlgString = config.readAppProperty(AppProperty.SECURITY_DEFAULT_EPHEMERAL_HASH_ALG);
            defaultHashAlorithm = Helper.readEnumFromString(PwmHashAlgorithm.class, PwmHashAlgorithm.SHA512, defaultHashAlgString);
            LOGGER.debug("using default ephemeral hash algorithm: "+ defaultHashAlgString.toString());
        }
    }

    @Override
    public void close() {

    }

    @Override
    public List<HealthRecord> healthCheck() {
        return null;
    }

    @Override
    public ServiceInfo serviceInfo() {
        return null;
    }

    public PwmBlockAlgorithm getDefaultBlockAlgorithm() {
        return defaultBlockAlgorithm;
    }

    public PwmHashAlgorithm getDefaultHashAlorithm() {
        return defaultHashAlorithm;
    }

    public String encryptToString(final String value)
            throws PwmUnrecoverableException
    {
        return SecureHelper.encryptToString(value, pwmSecurityKey, defaultBlockAlgorithm, SecureHelper.Flag.URL_SAFE);
    }

    public String decryptStringValue(
            final String value
    )
            throws PwmUnrecoverableException {
        return SecureHelper.decryptStringValue(value, pwmSecurityKey, defaultBlockAlgorithm, SecureHelper.Flag.URL_SAFE);
    }


    public String hash(
            final String input
    )
            throws PwmUnrecoverableException
    {
        return SecureHelper.hash(input, defaultHashAlorithm);
    }

    public String hash(
            final File file
    )
            throws IOException, PwmUnrecoverableException
    {
        return SecureHelper.hash(file, defaultHashAlorithm);
    }
}
