package password.pwm.config.function;

import password.pwm.config.PwmSetting;

public class OAuthCertImportFunction extends AbstractUriCertImportFunction {

    @Override
    PwmSetting getSetting() {
        return PwmSetting.OAUTH_ID_CODERESOLVE_URL;
    }
}
