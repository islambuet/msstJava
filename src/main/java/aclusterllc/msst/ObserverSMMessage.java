package aclusterllc.msst;

import org.json.JSONObject;

public interface ObserverSMMessage {
    public void processSMMessage(JSONObject jsonSMMessage,JSONObject info);
}
