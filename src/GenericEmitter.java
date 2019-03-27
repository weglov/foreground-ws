package com.wsforeground.plugin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.lang.reflect.Type;
import io.socket.client.Ack;
import io.socket.emitter.Emitter;

public abstract class GenericEmitter<T> implements Emitter.Listener {

   public Gson gson = new Gson();

   @Override
   public void call(Object... args) {
       if (args.length > 0 && args[0] instanceof JSONObject) {
           JSONObject data = (JSONObject) args[0];
           Type type = new TypeToken<T>() {}.getType();
           ack(data, args);
           data(gson.fromJson(data.toString(), type));
       }
   }

   private void ack(Object data, Object... args) {
       try {
           if (args[args.length - 1] instanceof Ack) {
               Ack ack = (Ack) args[args.length - 1];
               ack.call(data);
           }
       } catch (Exception ignored) { }
   }

   public abstract void data(T data);
}