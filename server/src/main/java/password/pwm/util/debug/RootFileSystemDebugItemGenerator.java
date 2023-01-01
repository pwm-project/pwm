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

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

class RootFileSystemDebugItemGenerator implements AppItemGenerator
{
    @Override
    public String getFilename()
    {
        return "filesystem-data.json";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream )
            throws IOException
    {
        final Collection<RootFileSystemInfo> rootInfos = RootFileSystemInfo.forAllRootFileSystems();
        outputStream.write( JsonFactory.get().serializeCollection( rootInfos, JsonProvider.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }

    @Value
    @Builder
    private static class RootFileSystemInfo implements Serializable
    {
        private String name;
        private String type;
        private long totalSpace;
        private long freeSpace;
        private long usableSpace;

        static Collection<RootFileSystemInfo> forAllRootFileSystems()
                throws IOException
        {
            final Iterator<FileStore> fileStoreIterator = FileSystems.getDefault().getFileStores().iterator();

            final List<RootFileSystemInfo> returnList = new ArrayList<>();
            while ( fileStoreIterator.hasNext() )
            {
                final FileStore fileStore = fileStoreIterator.next();
                final RootFileSystemInfo rootFileSystemInfo = RootFileSystemInfo.forRoot( fileStore );
                returnList.add( rootFileSystemInfo );
            }

            return List.copyOf( returnList );
        }

        static RootFileSystemInfo forRoot( final FileStore fileRoot )
                throws IOException
        {
            return RootFileSystemInfo.builder()
                    .name( fileRoot.name() )
                    .type( fileRoot.type() )
                    .totalSpace( fileRoot.getTotalSpace() )
                    .freeSpace( fileRoot.getUnallocatedSpace() )
                    .usableSpace( fileRoot.getUsableSpace() )
                    .build();
        }
    }
}
