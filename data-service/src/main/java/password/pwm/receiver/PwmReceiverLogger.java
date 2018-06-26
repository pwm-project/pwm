package password.pwm.receiver;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PwmReceiverLogger
{
    private final Class clazz;

    private PwmReceiverLogger( final Class clazz )
    {
        this.clazz = clazz;
    }

    public static PwmReceiverLogger forClass( final Class clazz )
    {
        return new PwmReceiverLogger( clazz );
    }

    public void debug(final CharSequence logMsg) {
        log( Level.FINE, logMsg, null );
    }

    public void info(final CharSequence logMsg) {
        log( Level.INFO, logMsg, null );
    }

    public void error(final CharSequence logMsg ) {
        log( Level.SEVERE, logMsg, null );
    }

    public void error(final CharSequence logMsg, final Throwable throwable ) {
        log( Level.SEVERE, logMsg, throwable );
    }

    private void log( final Level level, final CharSequence logMsg, final Throwable throwable ) {
        final Logger logger = Logger.getLogger(clazz.getName());
        logger.log( level, logMsg.toString(), throwable );
    }
}
