/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util.otp;

import java.util.*;


/**
 *
 * @author Menno Pieters
 */
public class OTPPamUtil {

    public static List<String> splitLines(String text) {
        List<String> list = new ArrayList<String>();
        if (text != null) {
            String lines[] = text.split("\r?\n|\r");
            list.addAll(Arrays.asList(lines));
        }
        return list;
    }

    public static OTPUserRecord decomposePamData(String otpInfo) {
        List<String> lines = splitLines(otpInfo);
        if (lines.size() >= 2) {
            Iterator<String> iterator = lines.iterator();
            String line = iterator.next();
            if (line.matches("^[A-Z2-7\\=]{16}$")) {
                OTPUserRecord otp = new OTPUserRecord(); // default identifier
                otp.setSecret(line);
                List<String> recoveryCodes = new ArrayList<String>();
                while (iterator.hasNext()) {
                    line = iterator.next();
                    if (line.startsWith("\" ")) {
                        String option = line.substring(2).trim();
                        if ("TOTP_AUTH".equals(option)) {
                            otp.setType(OTPUserRecord.Type.TOTP);
                        } else if (option.matches("^HOTP_COUNTER\\s+\\d+$")) {
                            String countStr = option.substring(option.indexOf(" ") + 1);
                            otp.setType(OTPUserRecord.Type.HOTP);
                            otp.setAttemptCount(Long.parseLong(countStr));
                        }
                    }
                    else if(line.matches("^\\d{8}$")) {
                        recoveryCodes.add(line);
                    }
                }
                if (!recoveryCodes.isEmpty()) {
                    otp.setRecoveryCodes(null);
                }
                return otp;
            }
        }
        return null;
    }

    public static String composePamData(OTPUserRecord otp) {
        if (otp == null) {
            return "";
        }
        String secret = otp.getSecret();
        OTPUserRecord.Type type = otp.getType();
        //List<String> recoveryCodes = otp.getRecoveryCodes();
        List<String> recoveryCodes = Collections.emptyList();
        String pamData = secret + "\n";
        if (OTPUserRecord.Type.HOTP.equals(type)) {
            pamData += String.format("\" HOTP_COUNTER %d\n", otp.getAttemptCount());
        } else {
            pamData += "\" TOTP_AUTH\n";
        }
        if (recoveryCodes != null && recoveryCodes.size() > 0) {
            for (String code : recoveryCodes) {
                pamData += code + "\n";
            }
        }
        return pamData;
    }
}
