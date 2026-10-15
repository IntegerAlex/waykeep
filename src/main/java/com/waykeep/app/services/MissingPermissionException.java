package com.waykeep.app.services;

import com.waykeep.app.util.PermissionRequester;

public class MissingPermissionException extends RuntimeException {

    private final PermissionRequester permissionRequester;

    public MissingPermissionException(PermissionRequester permissionRequester) {
        this.permissionRequester = permissionRequester;
    }
}
