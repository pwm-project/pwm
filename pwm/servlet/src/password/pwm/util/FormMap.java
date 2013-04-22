/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.util;

import java.io.Serializable;
import java.util.*;

public class FormMap implements Serializable,Map<String, String> {
    private HashMap<String,String> backingMap = new HashMap<String,String>();

    public FormMap() {
    }

    public FormMap(final Map inputStringMap) {
        if (inputStringMap != null) {
            for (final Object key : inputStringMap.keySet()) {
                if (key != null) {
                    final Object value = inputStringMap.get(key);
                    if (value != null) {
                        backingMap.put(key.toString(),value.toString());
                    }
                }
            }
        }
    }

    @Override
    public int size() {
        return backingMap.size();
    }

    @Override
    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return backingMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return backingMap.containsKey(value);
    }

    @Override
    public String get(Object key) {
        return backingMap.get(key);
    }

    public String get(String key, String defaultValue) {
        return backingMap.containsKey(key) ? backingMap.get(key) : defaultValue;
    }

    @Override
    public String put(String key, String value) {
        return backingMap.put(key,value);
    }

    @Override
    public String remove(Object key) {
        return backingMap.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        backingMap.putAll(m);
    }

    @Override
    public void clear() {
        backingMap.clear();
    }

    @Override
    public Set<String> keySet() {
        return backingMap.keySet();
    }

    @Override
    public Collection<String> values() {
        return backingMap.values();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return backingMap.entrySet();
    }
}
