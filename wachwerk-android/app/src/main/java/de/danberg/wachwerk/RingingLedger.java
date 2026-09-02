package de.danberg.wachwerk;
import org.json.JSONArray;
import org.json.JSONObject;

/** Serializable state machine; removing an activity never changes this ledger. */
final class RingingLedger {
    private final JSONArray queue,handled;
    RingingLedger(String saved) {
        JSONObject state;
        try { state=new JSONObject(saved); } catch(Exception e) { state=new JSONObject(); }
        queue=state.optJSONArray("queue")==null?new JSONArray():state.optJSONArray("queue");
        handled=state.optJSONArray("handled")==null?new JSONArray():state.optJSONArray("handled");
    }
    String id() { JSONObject head=queue.optJSONObject(0);return head==null?"":head.optString("id"); }
    String payload() { JSONObject head=queue.optJSONObject(0);return head==null?"":head.optString("payload"); }
    boolean enqueue(String id,String payload) {
        if(id==null || id.isEmpty())return false;
        for(int i=0;i<handled.length();i++)if(id.equals(handled.optString(i)))return false;
        for(int i=0;i<queue.length();i++)if(id.equals(queue.optJSONObject(i).optString("id")))return false;
        try { queue.put(new JSONObject().put("id",id).put("payload",payload));return true; }
        catch(Exception e) { return false; }
    }
    boolean acknowledge(String id) {
        if(id==null || id.isEmpty() || !id.equals(id()))return false;
        queue.remove(0);handled.put(id);
        while(handled.length()>64)handled.remove(0);
        return true;
    }
    String serialize() {
        try { return new JSONObject().put("queue",queue).put("handled",handled).toString(); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
}
