/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.cr.storage;

import java.text.SimpleDateFormat;

public class CrStorageXmlParser {
    static final String SALT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    final static String XML_NODE_ROOT = "ResponseSet";
    final static String XML_ATTRIBUTE_MIN_RANDOM_REQUIRED = "minRandomRequired";
    final static String XML_ATTRIBUTE_LOCALE = "locale";

    final static String XML_NODE_RESPONSE = "response";
    final static String XML_NODE_HELPDESK_RESPONSE = "helpdesk-response";
    final static String XML_NODE_CHALLENGE = "challenge";
    final static String XML_NODE_ANSWER_VALUE = "answer";

    final static String XML_ATTRIBUTE_VERSION = "version";
    final static String XML_ATTRIBUTE_CHAI_VERSION = "chaiVersion";
    final static String XML_ATTRIBUTE_ADMIN_DEFINED = "adminDefined";
    final static String XML_ATTRIBUTE_REQUIRED = "required";
    final static String XML_ATTRIBUTE_HASH_COUNT = "hashcount";
    final static String XML_ATTRIBUTE_CONTENT_FORMAT = "format";
    final static String XML_ATTRIBUTE_SALT = "salt";
    final static String XNL_ATTRIBUTE_MIN_LENGTH = "minLength";
    final static String XNL_ATTRIBUTE_MAX_LENGTH = "maxLength";
    final static String XML_ATTRIBUTE_CASE_INSENSITIVE = "caseInsensitive";
    final static String XML_ATTRIBUTE_CHALLENGE_SET_IDENTIFER = "challengeSetID"; // identifier from challenge set.
    final static String XML_ATTRIBUTE_TIMESTAMP = "time";

    final static String VALUE_VERSION = "2";

    final static SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

}
