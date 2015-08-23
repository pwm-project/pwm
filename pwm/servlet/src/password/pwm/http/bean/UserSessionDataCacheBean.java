package password.pwm.http.bean;

import password.pwm.Permission;
import password.pwm.util.PostChangePasswordAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserSessionDataCacheBean implements PwmSessionBean {
    private Map<Permission, Permission.PERMISSION_STATUS> permissions = new HashMap<>();
    private Map<String, PostChangePasswordAction> postChangePasswordActions = new HashMap<>();

    public void clearPermissions()
    {
        permissions.clear();
    }

    public Permission.PERMISSION_STATUS getPermission(final Permission permission)
    {
        final Permission.PERMISSION_STATUS status = permissions.get(permission);
        return status == null ? Permission.PERMISSION_STATUS.UNCHECKED : status;
    }

    public void setPermission(
            final Permission permission,
            final Permission.PERMISSION_STATUS status
    )
    {
        permissions.put(permission, status);
    }

    public Map<Permission, Permission.PERMISSION_STATUS> getPermissions()
    {
        return permissions;
    }

    public void setPermissions(final Map<Permission, Permission.PERMISSION_STATUS> permissions)
    {
        this.permissions = permissions;
    }

    public void addPostChangePasswordActions(
            final String key,
            final PostChangePasswordAction postChangePasswordAction
    )
    {
        if (postChangePasswordAction == null) {
            postChangePasswordActions.remove(key);
        } else {
            postChangePasswordActions.put(key, postChangePasswordAction);
        }
    }

    public List<PostChangePasswordAction> removePostChangePasswordActions()
    {
        final List<PostChangePasswordAction> copiedList = new ArrayList<>();
        copiedList.addAll(postChangePasswordActions.values());
        postChangePasswordActions.clear();
        return copiedList;
    }

}
