package password.pwm.util.secure;

import org.junit.Test;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ResourceBundle;

public class PromiscuousTrustManagerTest
{

    @Test
    public void createPromiscuousTrustManager() throws CertificateException, IOException
    {
        final ResourceBundle bundle = ResourceBundle.getBundle( PromiscuousTrustManagerTest.class.getName() );
        final String cert1data = bundle.getString( "cert1" );
        final X509Certificate cert1 = X509Utils.certificateFromBase64( cert1data );

        final X509TrustManager trustManager = PromiscuousTrustManager.createPromiscuousTrustManager();
        final X509Certificate[] certificates = new X509Certificate[]
                {
                        cert1
                };
        trustManager.checkServerTrusted( certificates, "test" );
    }

}