package de.dennisguse.opentracks.services;

import de.dennisguse.opentracks.util.PermissionRequester;

public class MissingPermissionException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L; // Recommended for serializable classes

    private final transient PermissionRequester permissionRequester;
    

    public MissingPermissionException(PermissionRequester permissionRequester) {
        this.permissionRequester = permissionRequester;
    }
}
