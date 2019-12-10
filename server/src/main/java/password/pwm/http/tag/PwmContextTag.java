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

package password.pwm.http.tag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

public class PwmContextTag extends TagSupport
{

    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        try
        {
            final HttpServletRequest req = ( HttpServletRequest ) pageContext.getRequest();
            pageContext.getOut().write( req.getContextPath() );
        }
        catch ( final Exception e )
        {
            throw new JspTagException( e.getMessage() );
        }
        return EVAL_PAGE;
    }
}
