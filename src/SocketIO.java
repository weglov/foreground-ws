package com.wsforeground.plugin;

import com.google.gson.internal.LinkedTreeMap;

import io.reactivex.subjects.PublishSubject;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import okhttp3.OkHttpClient;

import static com.wsforeground.plugin.SocketEvent.Event.CHANGED;
import static com.wsforeground.plugin.SocketEvent.Event.NEW;
import static com.wsforeground.plugin.SocketEvent.Event.STATUS_CHANGED;
import static io.socket.client.Socket.EVENT_PING;
import static io.socket.client.Socket.EVENT_PONG;
import static io.socket.engineio.client.transports.WebSocket.NAME;


public class SocketIO{

    String EVENT_CHANGE_ORDER_STATUS = "changeOrderStatus";
    String EVENT_CHANGE_ORDER_ITEM_LIST = "changeOrderItemList";
    String EVENT_NEW_ORDER = "newOrder";

    private Socket socket;

    private OkHttpClient client;

    private PublishSubject<SocketEvent> publisher = PublishSubject.create();

    private static final Object semaphore = new Object();

    private String url;
    private String token;

    public SocketIO(String url, String token, OkHttpClient client) {
        this.url = url;
        this.token = token;
        this.client = client;
    }

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
                if (orderId != null && !orderId.isEmpty()) {
                    event.id = orderId;
                }
                String orderStatus = (String) map.get("orderStatus");
                if (orderStatus != null && !orderStatus.isEmpty()) {
                    event.status = orderStatus;
                }
            }
        } catch (ClassCastException e) {
        }
    }

    public PublishSubject<SocketEvent> getUpdates() {
        return publisher;
    }

}