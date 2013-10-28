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

package password.pwm.ws.server.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.bean.UserStatusCacheBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditRecord;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.SimpleDateFormat;
import java.util.*;

@Path("/report")
public class RestUserReportServer {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetAppAuditData(
            @Context HttpServletRequest request,
            @QueryParam("maximum") int maximum
    ) throws ChaiUnavailableException, PwmUnrecoverableException {
        maximum = maximum > 0 ? maximum : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, RestServerHelper.ServiceType.AUTH_REQUIRED, null);
        } catch (PwmUnrecoverableException e) {
            return Response.ok(RestServerHelper.outputJsonErrorResult(e.getErrorInformation(),request)).build();
        }

        if (!Permission.checkPermission(Permission.PWMADMIN, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
            final ErrorInformation errorInfo = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return Response.ok(RestResultBean.fromErrorInformation(errorInfo, restRequestBean.getPwmApplication(), restRequestBean.getPwmSession()).toJson()).build();
        }

        final ArrayList<UserStatusCacheBean> reportData = new ArrayList<UserStatusCacheBean>();
        final Iterator<UserStatusCacheBean> cacheBeanIterator = restRequestBean.getPwmApplication().getUserStatusCacheManager().iterator();
        while (cacheBeanIterator.hasNext() && reportData.size() < maximum) {
            final UserStatusCacheBean userStatusCacheBean = cacheBeanIterator.next();
            if (userStatusCacheBean != null) {
                reportData.add(userStatusCacheBean);
            }
        }

        final HashMap<String,Object> returnData = new HashMap<String, Object>();
        returnData.put("users",reportData);

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnData);
        return Response.ok(restResultBean.toJson()).build();
    }


}
