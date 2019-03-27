package com.wsforeground.plugin;

import com.google.gson.internal.LinkedTreeMap;

import javax.inject.Inject;

import io.reactivex.subjects.PublishSubject;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import okhttp3.OkHttpClient;
import ru.foodfox.vendor.BuildConfig;
import ru.foodfox.vendor.rx.GenericEmitter;
import ru.foodfox.vendor.utils.NetworkChecker;
import ru.foodfox.vendor.utils.TextUtils;

import static io.socket.client.Socket.EVENT_CONNECT;
import static io.socket.client.Socket.EVENT_CONNECT_ERROR;
import static io.socket.client.Socket.EVENT_DISCONNECT;
import static io.socket.client.Socket.EVENT_PING;
import static io.socket.client.Socket.EVENT_PONG;
import static io.socket.client.Socket.EVENT_RECONNECT;
import static io.socket.engineio.client.transports.WebSocket.NAME;
import static ru.foodfox.vendor.params.Params.LogType.SOCKET_CONNECT;
import static ru.foodfox.vendor.params.Params.LogType.SOCKET_NEW_ORDER;
import static ru.foodfox.vendor.params.Params.LogType.SOCKET_ORDER_CHANGED;
import static ru.foodfox.vendor.params.Params.LogType.SOCKET_ORDER_STATUS_CHANGED;
import static ru.foodfox.vendor.params.Params.LogType.SOCKET_PONG;
import static ru.foodfox.vendor.params.Params.LogType.SOCKET_RECONNECT;
import static ru.foodfox.vendor.params.Params.LogType.SOCKET_RESTAURANT_TOGGLE;
import static ru.foodfox.vendor.params.Params.MetricaEvent.ConnectionError.CONNECTION_PROBLEM;
import static ru.foodfox.vendor.params.Params.MetricaEvent.ConnectionError.DEVICE_OFFLINE;
import static ru.foodfox.vendor.params.Params.MetricaEvent.EventTitles.EVENT_NOTIFIER_CONNECTED;
import static ru.foodfox.vendor.params.Params.MetricaEvent.EventTitles.EVENT_NOTIFIER_CONNECTED_ERROR;
import static ru.foodfox.vendor.params.Params.MetricaEvent.EventTitles.EVENT_NOTIFIER_DISCONNECTED;
import static ru.foodfox.vendor.params.Params.Socket.Events.EVENT_CHANGE_MENU_FAIL;
import static ru.foodfox.vendor.params.Params.Socket.Events.EVENT_CHANGE_ORDER_ITEM_LIST;
import static ru.foodfox.vendor.params.Params.Socket.Events.EVENT_CHANGE_ORDER_STATUS;
import static ru.foodfox.vendor.params.Params.Socket.Events.EVENT_NEW_ORDER;
import static ru.foodfox.vendor.params.Params.Socket.Events.EVENT_RESTAURANT_TOGGLE;
import static ru.foodfox.vendor.service.SocketEvent.Event.CHANGED;
import static ru.foodfox.vendor.service.SocketEvent.Event.CHANGE_MENU_FAIL;
import static ru.foodfox.vendor.service.SocketEvent.Event.CONNECTED;
import static ru.foodfox.vendor.service.SocketEvent.Event.CONNECTION_ERROR;
import static ru.foodfox.vendor.service.SocketEvent.Event.DISCONNECTED;
import static ru.foodfox.vendor.service.SocketEvent.Event.NEW;
import static ru.foodfox.vendor.service.SocketEvent.Event.RESTAURANT_TOGGLE;
import static ru.foodfox.vendor.service.SocketEvent.Event.STATUS_CHANGED;

/**
 * Created by FromTheSeventhSky on 25.02.2018.
 */

public class SocketIO implements SocketInterface {

    private Socket socket;

    private OkHttpClient client;

    private PublishSubject<SocketEvent> publisher = PublishSubject.create();

    private static final Object semaphore = new Object();

    private String url;
    private String token;

    /*private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }
    }};*/

    @Inject
    public SocketIO(String url, String token, OkHttpClient client) {
        this.url = url;
        this.token = token;
        this.client = client;
    }

    @Override
    public void init(String query) {

        synchronized (semaphore) {

            IO.setDefaultOkHttpWebSocketFactory(client);

            try {
                IO.Options opts = new IO.Options();
                opts.forceNew = true;
                opts.transports = new String[]{NAME};
                opts.reconnection = true;
                opts.reconnectionAttempts = Integer.MAX_VALUE;
                opts.reconnectionDelay = 2000;
                opts.reconnectionDelayMax = 7000;
                opts.query = "token=" + token + query;
                /*SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                //opts.sslContext = sslContext;*/
                socket = IO.socket(url, opts);

                socket.on(EVENT_PING, onPing);
                socket.on(EVENT_CHANGE_ORDER_STATUS, onOrderStatusChange);
                socket.on(EVENT_CHANGE_ORDER_ITEM_LIST, onOrderChanged);
                socket.on(EVENT_NEW_ORDER, onNewOrder);
                socket.connect();
            } catch (Exception e) {
                // Timber.e(e);
            }
        }
    }

    @Override
    public void done() {
        synchronized (semaphore) {
            if (socket == null) {
                return;
            }
            if (socket.connected()) {
                socket.close();
            }
        }
    }

    private String getMessage(Object[] args) {
        String logMessage = "";
        if (args != null && args.length > 0) {
            try {
                logMessage = args[0].toString();
            } catch (Exception e) {
                // Timber.e(e);
            }
        }
        return logMessage;
    }

    private String getMessage(Object arg) {
        String logMessage = "";
        if (arg != null) {
            try {
                logMessage = arg.toString();
            } catch (Exception e) {
                // Timber.e(e);
            }
        }
        return logMessage;
    }

    private Emitter.Listener onPing = args -> {
        synchronized (semaphore) {
            if (socket != null) {
                socket.emit(EVENT_PONG);
            }
        }
    };

    private GenericEmitter onNewOrder = new GenericEmitter() {
        @Override
        public void data(Object data) {
            SocketEvent event = new SocketEvent(NEW);
            fillEvent(data, event);
            publisher.onNext(event);
        }
    };

    private GenericEmitter onOrderStatusChange = new GenericEmitter() {
        @Override
        public void data(Object data) {
            SocketEvent event = new SocketEvent(STATUS_CHANGED);
            fillEvent(data, event);
            publisher.onNext(event);
        }
    };

    private GenericEmitter onOrderChanged = new GenericEmitter() {
        @Override
        public void data(Object data) {
            SocketEvent event = new SocketEvent(CHANGED);
            fillEvent(data, event);
            publisher.onNext(event);
        }
    };

    private void fillEvent(Object data, SocketEvent event) {
        try {
            if (data != null && data instanceof LinkedTreeMap) {
                LinkedTreeMap map = (LinkedTreeMap) data;
                String orderId = (String) map.get("orderId");
                if (TextUtils.notEmpty(orderId)) {
                    event.id = orderId;
                }
                String orderStatus = (String) map.get("orderStatus");
                if (TextUtils.notEmpty(orderStatus)) {
                    event.status = orderStatus;
                }
            }
        } catch (ClassCastException e) {
        }
    }

    @Override
    public PublishSubject<SocketEvent> getUpdates() {
        return publisher;
    }

}