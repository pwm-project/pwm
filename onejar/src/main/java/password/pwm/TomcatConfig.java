package password.pwm;

import java.io.File;
import java.io.InputStream;

class TomcatConfig {
    private int port;
    private File applicationPath;
    private File workingPath;
    private InputStream war;
    private String context;

    public int getPort( )
    {
        return port;
    }

    public void setPort( final int port )
    {
        this.port = port;
    }

    public File getApplicationPath( )
    {
        return applicationPath;
    }

    public void setApplicationPath( final File applicationPath )
    {
        this.applicationPath = applicationPath;
    }

    public File getWorkingPath( )
    {
        return workingPath;
    }

    public void setWorkingPath( final File workingPath )
    {
        this.workingPath = workingPath;
    }

    public InputStream getWar( )
    {
        return war;
    }

    public void setWar( final InputStream war )
    {
        this.war = war;
    }

    public String getContext( )
    {
        return context;
    }

    public void setContext( final String context )
    {
        this.context = context;
    }
}
