/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.util.localdb;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class LocalDBExtendedTest
{

    @ClassRule
    public static TemporaryFolder testFolder = new TemporaryFolder();

    private static final LocalDB.DB TEST_DB = LocalDB.DB.TEMP;
    private static LocalDB localDB;

    @BeforeClass
    public static void setUp() throws Exception
    {
        TestHelper.setupLogging();
        final File fileLocation = testFolder.newFolder( "localdb-test" );
        localDB = LocalDBFactory.getInstance( fileLocation, false, null, null );
        localDB.truncate( TEST_DB );
        Assert.assertEquals( 0, localDB.size( TEST_DB ) );
    }

    @Test
    public void testPut() throws LocalDBException
    {
        Assert.assertNull( localDB.get( TEST_DB, "testKey1" ) );
        localDB.put( TEST_DB, "testKey1", "testValue1" );
        Assert.assertEquals( localDB.get( TEST_DB, "testKey1" ), "testValue1" );
    }

    @Test
    public void testSize() throws LocalDBException
    {
        final long startTime = System.currentTimeMillis();
        for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
        {
            final long size = localDB.size( loopDB );
            //System.out.println( loopDB + " size=" + size );
        }
        //System.out.println( "total duration: " + TimeDuration.fromCurrent( startTime ).asLongString() );
    }

    @AfterClass
    public static void tearDown() throws Exception
    {
        if ( localDB != null )
        {
            localDB.close();
            localDB = null;
        }
    }
}
