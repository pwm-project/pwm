package password.pwm.config.stored;

import password.pwm.error.PwmUnrecoverableException;

import java.io.InputStream;
import java.io.OutputStream;

interface StoredConfigurationProvider {
    StoredConfiguration fromXml(final InputStream inputStream) throws PwmUnrecoverableException;

    void toXml(final OutputStream outputStream);
}
