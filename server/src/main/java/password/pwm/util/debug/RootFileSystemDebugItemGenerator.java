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
import password.pwm.util.json.JsonProvider;
import password.pwm.util.json.JsonFactory;

import java.io.File;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

class RootFileSystemDebugItemGenerator implements AppItemGenerator
{
    @Override
    public String getFilename()
    {
        return "filesystem-data.json";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {
        final Collection<RootFileSystemInfo> rootInfos = RootFileSystemInfo.forAllRootFileSystems();
        outputStream.write( JsonFactory.get().serializeCollection( rootInfos, JsonProvider.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }

    @Value
    @Builder
    private static class RootFileSystemInfo implements Serializable
    {
        private String rootPath;
        private long totalSpace;
        private long freeSpace;
        private long usableSpace;

        static Collection<RootFileSystemInfo> forAllRootFileSystems()
        {
            return Arrays.stream( File.listRoots() )
                    .map( RootFileSystemInfo::forRoot )
                    .collect( Collectors.toList() );
        }

        static RootFileSystemInfo forRoot( final File fileRoot )
        {
            return RootFileSystemInfo.builder()
                    .rootPath( fileRoot.getAbsolutePath() )
                    .totalSpace( fileRoot.getTotalSpace() )
                    .freeSpace( fileRoot.getFreeSpace() )
                    .usableSpace( fileRoot.getUsableSpace() )
                    .build();
        }
    }
}
