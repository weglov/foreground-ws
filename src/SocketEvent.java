package com.wsforeground.plugin;

public class SocketEvent {

  public Event event;
  public String id = "";
  public String status = "";

  public SocketEvent(Event event) {
      this.event = event;
  }

  public SocketEvent setId(String id) {
      this.id = id;
      return this;
  }

  public SocketEvent setStatus(String status) {
      this.status = status;
      return this;
  }


  public enum Event {
      NEW, CHANGED, STATUS_CHANGED, CONNECTED, DISCONNECTED, RESTAURANT_TOGGLE, CONNECTION_ERROR, CHANGE_MENU_FAIL
  }

}