package password.pwm.svc.wordlist;

import password.pwm.PwmApplication;

public enum WordlistType {
    WORDLIST,
    SEEDLIST,

    ;

    public Wordlist forType(final PwmApplication pwmApplication) {
        switch (this) {
            case WORDLIST:
                return pwmApplication.getWordlistManager();

            case SEEDLIST:
                return pwmApplication.getSeedlistManager();

            default:
                throw new IllegalStateException("unhandled wordlistType");
        }

    }
}
