/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.debug;

import password.pwm.PwmConstants;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

final class RootFileSystemDebugItemGenerator implements AppItemGenerator
{
    @Override
    public String getFilename()
    {
        return "filesystem-data.json";
    }

    @Override
    public void outputItem( final AppDebugItemRequest debugItemInput, final OutputStream outputStream )
            throws IOException
    {
        final Collection<RootFileSystemInfo> rootInfos = forAllRootFileSystems( debugItemInput );
        outputStream.write( JsonFactory.get().serializeCollection( rootInfos, JsonProvider.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }

    private List<RootFileSystemInfo> forAllRootFileSystems( final AppDebugItemRequest appDebugItemInput )
    {
        final Iterator<FileStore> fileStoreIterator = FileSystems.getDefault().getFileStores().iterator();

        final List<RootFileSystemInfo> returnList = new ArrayList<>();
        while ( fileStoreIterator.hasNext() )
        {
            final FileStore fileStore = fileStoreIterator.next();
            try
            {
                final RootFileSystemInfo rootFileSystemInfo = RootFileSystemInfo.forRoot( fileStore );
                returnList.add( rootFileSystemInfo );
            }
            catch ( final Throwable t )
            {
                final String errorMsg = "error during root filesystem debug reading: " + t.getMessage();
                appDebugItemInput.logger().error( this, () -> errorMsg );
            }
        }

        return List.copyOf( returnList );
    }

    private record RootFileSystemInfo(
            String name,
            String type,
            long totalSpace,
            long freeSpace,
            long usableSpace
    )
    {
        static RootFileSystemInfo forRoot( final FileStore fileRoot )
                throws IOException
        {
            return new RootFileSystemInfo(
                     fileRoot.name(),
                     fileRoot.type(),
                     fileRoot.getTotalSpace(),
                     fileRoot.getUnallocatedSpace(),
                     fileRoot.getUsableSpace() );
        }
    }
}
