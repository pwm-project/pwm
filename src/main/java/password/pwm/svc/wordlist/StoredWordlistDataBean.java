/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.svc.wordlist;

import java.io.Serializable;
import java.util.Date;

public class StoredWordlistDataBean implements Serializable {
    private boolean completed;
    private Source source;
    private Date storeDate;
    private String sha1hash;
    private int size;

    public enum Source {
        BuiltIn("Built-In"),
        AutoImport("Import from configured URL"),
        User("Uploaded"),

        ;

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public boolean isCompleted() {
        return completed;
    }

    public Source getSource() {
        return source;
    }

    public Date getStoreDate() {
        return storeDate;
    }

    public String getSha1hash() {
        return sha1hash;
    }

    public int getSize() {
        return size;
    }

    public StoredWordlistDataBean(boolean completed, Source source, Date storeDate, String sha1hash, int size) {
        this.completed = completed;
        this.source = source;
        this.storeDate = storeDate;
        this.sha1hash = sha1hash;
        this.size = size;
    }

    public static class Builder {
        private boolean completed;
        private Source source;
        private Date storeDate;
        private String sha1hash;
        private int size;

        public Builder() {
        }

        public Builder(final StoredWordlistDataBean source) {
            this.completed = source.completed;
            this.source = source.source;
            this.storeDate = source.storeDate;
            this.sha1hash = source.sha1hash;
            this.size = source.size;
        }

        public Builder setCompleted(boolean completed) {
            this.completed = completed;
            return this;
        }

        public Builder setSource(Source source) {
            this.source = source;
            return this;
        }

        public Builder setStoreDate(Date storeDate) {
            this.storeDate = storeDate;
            return this;
        }

        public Builder setSha1hash(String sha1hash) {
            this.sha1hash = sha1hash;
            return this;
        }

        public Builder setSize(int size) {
            this.size = size;
            return this;
        }

        public StoredWordlistDataBean create() {
            return new StoredWordlistDataBean(completed, source, storeDate, sha1hash, size);
        }
    }
}
