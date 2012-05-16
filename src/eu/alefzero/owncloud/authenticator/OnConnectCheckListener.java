package eu.alefzero.owncloud.authenticator;

public interface OnConnectCheckListener {

    enum ResultType {
        OK, OK_NO_SSL, SSL_INIT_ERROR, HOST_NOT_AVAILABLE, TIMEOUT, NO_NETWORK_CONNECTION, INORRECT_ADDRESS, INSTANCE_NOT_CONFIGURED, FILE_NOT_FOUND, UNKNOWN_ERROR
    }

    public void onConnectionCheckResult(ResultType type);

}
