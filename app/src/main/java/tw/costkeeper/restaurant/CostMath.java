package tw.costkeeper.restaurant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/** All costs expressed in NT dollars, base unit g / ml / item. */
public final class CostMath {
    private CostMath() {}
    public static String baseUnit(String unit) {
        if (unit.equals("公斤") || unit.equals("kg") || unit.equals("克") || unit.equals("g")) return "克";
        if (unit.equals("公升") || unit.equals("L") || unit.equals("毫升") || unit.equals("ml")) return "毫升";
        return unit;
    }
    public static double toBase(double qty, String unit) {
        if (unit.equals("公斤") || unit.equals("kg") || unit.equals("公升") || unit.equals("L")) return qty * 1000.0;
        return qty;
    }
    public static double unitCost(JSONObject item) {
        double qty = toBase(item.optDouble("packQty",0),item.optString("unit",""));
        return qty > 0 ? item.optDouble("price", 0) / qty : 0;
    }
    public static Map<String, JSONObject> ingredientMap(JSONObject db) {
        Map<String, JSONObject> result = new HashMap<>();
        JSONArray all = db.optJSONArray("ingredients");
        if (all != null) for (int i=0;i<all.length();i++) {
            JSONObject x=all.optJSONObject(i); if (x!=null) result.put(x.optString("id"),x);
        }
        return result;
    }
    public static double recipeCost(JSONObject recipe, JSONObject db) {
        Map<String,JSONObject> foods=ingredientMap(db);
        JSONArray lines=recipe.optJSONArray("lines");
        double sum=0;
        if (lines!=null) for(int i=0;i<lines.length();i++) {
            JSONObject x=lines.optJSONObject(i);
            if (x==null) continue;
            JSONObject food=foods.get(x.optString("ingredientId"));
            if(food!=null) sum+=unitCost(food)*x.optDouble("qty",0);
        }
        return sum;
    }
    public static double salePrice(JSONObject recipe, String channel) {
        if (channel.equals("Uber")) return recipe.optDouble("uberPrice",0);
        if (channel.equals("Foodpanda")) return recipe.optDouble("pandaPrice",0);
        return recipe.optDouble("price",0);
    }
    public static double feeRate(JSONObject recipe, String channel) {
        if (channel.equals("Uber")) return recipe.optDouble("uberFee",0)/100.0;
        if (channel.equals("Foodpanda")) return recipe.optDouble("pandaFee",0)/100.0;
        return 0;
    }
    public static double netProfit(JSONObject recipe, JSONObject db, String channel) {
        double price=salePrice(recipe,channel);
        return price * (1-feeRate(recipe,channel)) - recipeCost(recipe,db);
    }
    public static JSONObject find(JSONArray arr, String id) {
        for(int i=0;i<arr.length();i++) {JSONObject x=arr.optJSONObject(i); if(x!=null&&id.equals(x.optString("id")))return x;}
        return null;
    }
    public static void upsert(JSONArray arr,JSONObject item) {
        for(int i=0;i<arr.length();i++) if(item.optString("id").equals(arr.optJSONObject(i).optString("id"))) {
            try{arr.put(i,item);}catch(Exception e){throw new IllegalStateException(e);}return;
        }
        arr.put(item);
    }
    public static void remove(JSONArray arr,String id) {
        for(int i=arr.length()-1;i>=0;i--) {
            JSONObject item=arr.optJSONObject(i);
            if(item!=null&&id.equals(item.optString("id")))arr.remove(i);
        }
    }
}
