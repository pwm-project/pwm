package password.pwm.config.function;

import password.pwm.config.PwmSetting;

public class NAAFCertImportFunction extends AbstractUriCertImportFunction {

    @Override
    PwmSetting getSetting() {
        return PwmSetting.NAAF_WS_URL;
    }
}
