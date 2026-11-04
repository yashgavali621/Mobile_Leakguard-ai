package com.leakguard.ai.utils;

/**
 * Generic wrapper for anything a Repository hands back to a ViewModel through
 * LiveData, so every screen can render Loading / Success / Error in a
 * consistent way (see section 53/55 of the product spec: every network call
 * must expose loading, success and error states - never a raw exception).
 *
 * @param <T> the payload type on success.
 */
public final class Resource<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    private final Status status;
    private final T data;
    private final String message;

    /** Machine-readable failure reason, used to drive specific UI (e.g. show
     *  "verify your email" vs a generic error banner). Null unless status == ERROR. */
    private final ErrorType errorType;

    public enum ErrorType {
        NETWORK, TIMEOUT, UNAUTHORIZED, EMAIL_NOT_VERIFIED, VALIDATION, SERVER, UNKNOWN
    }

    private Resource(Status status, T data, String message, ErrorType errorType) {
        this.status = status;
        this.data = data;
        this.message = message;
        this.errorType = errorType;
    }

    public static <T> Resource<T> loading() {
        return new Resource<>(Status.LOADING, null, null, null);
    }

    public static <T> Resource<T> success(T data) {
        return new Resource<>(Status.SUCCESS, data, null, null);
    }

    public static <T> Resource<T> error(String message, ErrorType errorType) {
        return new Resource<>(Status.ERROR, null, message, errorType);
    }

    public Status getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
