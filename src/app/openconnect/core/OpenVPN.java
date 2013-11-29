package app.openconnect.core;

public class OpenVPN {

	public static void logError(String msg) {
	}

	public static void logError(int ressourceId, Object... args) {
	}

	public static void logInfo(String message) {
	}

	public static void logInfo(int ressourceId, Object... args) {
	}

    public enum ConnectionStatus {
        LEVEL_CONNECTED,
        LEVEL_VPNPAUSED,
        LEVEL_CONNECTING_SERVER_REPLIED,
        LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
        LEVEL_NONETWORK,
		LEVEL_NOTCONNECTED,
		LEVEL_AUTH_FAILED,
		LEVEL_WAITING_FOR_USER_INPUT,
		UNKNOWN_LEVEL
    }
}
