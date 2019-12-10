/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.config.stored;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import password.pwm.config.StoredValue;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Value
@Builder( toBuilder = true )
class StoredConfigData
{
    @Builder.Default
    private String createTime = "";

    @Builder.Default
    private Instant modifyTime = Instant.now();

    @Singular
    private Map<StoredConfigItemKey, StoredValue> storedValues;

    @Singular
    private Map<StoredConfigItemKey, ValueMetaData> metaDatas;

    @Value
    static class ValueAndMetaCarrier
    {
        private final StoredConfigItemKey key;
        private final StoredValue value;
        private final ValueMetaData metaData;
    }

    static Map<StoredConfigItemKey, ValueMetaData> carrierAsMetaDataMap( final Collection<ValueAndMetaCarrier> input )
    {
        return input.stream()
                .filter( ( t ) -> t.getKey() != null && t.getMetaData() != null )
                .collect( Collectors.toMap( StoredConfigData.ValueAndMetaCarrier::getKey, StoredConfigData.ValueAndMetaCarrier::getMetaData ) );
    }

    static Map<StoredConfigItemKey, StoredValue> carrierAsStoredValueMap( final Collection<ValueAndMetaCarrier> input )
    {
        return input.stream()
                .filter( ( t ) -> t.getKey() != null && t.getValue() != null )
                .collect( Collectors.toMap( StoredConfigData.ValueAndMetaCarrier::getKey, StoredConfigData.ValueAndMetaCarrier::getValue ) );
    }
}
