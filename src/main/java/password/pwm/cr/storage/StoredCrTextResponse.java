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

package password.pwm.cr.storage;

import com.novell.ldapchai.cr.bean.AnswerBean;
import org.jdom2.Element;

class StoredCrTextResponse implements StoredResponse {
    private String answer;
    private boolean caseInsensitive;

    StoredCrTextResponse(final String answer, final boolean caseInsensitive) {
        if (answer == null || answer.length() < 1) {
            throw new IllegalArgumentException("missing answer text");
        }

        this.answer = answer;
        this.caseInsensitive = caseInsensitive;
    }

    public Element toXml() {
        final Element answerElement = new Element(CrStorageXmlParser.XML_NODE_ANSWER_VALUE);
        answerElement.setText(answer);
        answerElement.setAttribute(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT, StoredResponseFormatType.TEXT.toString());
        return answerElement;
    }

    public boolean testAnswer(final String testResponse) {
        if (testResponse == null) {
            return false;
        }

        final String casedResponse = caseInsensitive ? testResponse.toLowerCase() : testResponse;
        return answer.equalsIgnoreCase(casedResponse);
    }

    static class TextAnswerFactory implements ImplementationFactory {
        public StoredCrTextResponse newStoredResponse(final StoredResponseFactory.AnswerConfiguration answerConfiguration, final String answer) {
            final boolean caseInsensitive = answerConfiguration.caseInsensitive;
            return new StoredCrTextResponse(answer,caseInsensitive);
        }

        public StoredResponse fromAnswerBean(AnswerBean input, String challengeText) {
            return new StoredCrTextResponse(input.getAnswerText(), input.isCaseInsensitive());
        }

        public StoredCrTextResponse fromXml(final Element element, final boolean caseInsensitive, final String challengeText) {
            final String answerValue = element.getText();
            return new StoredCrTextResponse(answerValue,caseInsensitive);
        }
    }
}
