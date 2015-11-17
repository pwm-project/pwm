/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2016 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.tests;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

public class MakeSelfSignedCertTest extends TestCase
{
	private static final Provider BC_PROVIDER = new BouncyCastleProvider();

	public void testSelfSignedCert() throws Exception
	{
		Security.addProvider(BC_PROVIDER);

		KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA", "BC");
		kpGen.initialize(2048, new SecureRandom());
		final KeyPair keyPair = kpGen.generateKeyPair();


		final String cnName = "test.myname.com";
		final long futureSeconds = TimeUnit.DAYS.toMillis(2 * 265);

		X509Certificate storedCertData = HttpsServerCertificateManager.SelfCertGenerator.generateV3Certificate(keyPair, cnName, futureSeconds);
		Assert.assertNotNull(storedCertData);
		Assert.assertEquals(storedCertData.getSubjectDN().getName(), storedCertData.getIssuerDN().getName());
	}
}
