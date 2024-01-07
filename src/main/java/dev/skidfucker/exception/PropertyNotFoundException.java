package dev.skidfucker.exception;

public class PropertyNotFoundException extends RuntimeException {

    public PropertyNotFoundException(final String cause) {
        super(cause);
    }
}
