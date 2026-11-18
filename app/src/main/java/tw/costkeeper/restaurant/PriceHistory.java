package tw.costkeeper.restaurant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Historical price observations by procurement source, stored exclusively in the local encrypted DB. */
public final class PriceHistory {
    public static final String ACTUAL="actual", HQ="hq";
    private PriceHistory() {}
    public static boolean isHq(String source){return HQ.equals(source);}
    public static String label(String source){return isHq(source)?"總部標準":"店內實際";}
    public static String source(JSONObject record){return HQ.equals(record.optString("source"))?HQ:ACTUAL;} // v1 records are actual
    public static boolean hasPrice(JSONObject food,String source){
        return !isHq(source) || (food.has("hqPrice")&&food.optDouble("hqPackQty",0)>0&&!food.optString("hqUnit").isEmpty());
    }
    public static double packPrice(JSONObject food,String source){
        return isHq(source)?food.optDouble("hqPrice",0):food.optDouble("price",0);
    }
    public static double packQty(JSONObject food,String source){
        return isHq(source)?food.optDouble("hqPackQty",0):food.optDouble("packQty",0);
    }
    public static String unit(JSONObject food,String source){
        return isHq(source)?food.optString("hqUnit"):food.optString("unit");
    }
    public static String supplier(JSONObject food,String source){
        return isHq(source)?food.optString("hqSupplier"):food.optString("supplier");
    }
    public static double unitCost(JSONObject food,String source){
        if(!hasPrice(food,source))return Double.NaN;
        double qty=CostMath.toBase(packQty(food,source),unit(food,source));
        return qty>0?packPrice(food,source)/qty:Double.NaN;
    }
    public static List<JSONObject> records(JSONObject db,String ingredientId,String source){
        List<JSONObject> all=new ArrayList<>();
        JSONArray purchases=db.optJSONArray("purchases");
        if(purchases==null)return all;
        for(int i=0;i<purchases.length();i++){
            JSONObject p=purchases.optJSONObject(i);
            if(p!=null&&ingredientId.equals(p.optString("ingredientId"))&&source(p).equals(source))all.add(p);
        }
        all.sort(Comparator.comparing((JSONObject p)->p.optString("date"))
            .thenComparing(p->p.optString("recordedAt")).thenComparing(p->p.optString("id")));
        return all;
    }
    /** A backdated observation is retained but does not overwrite a newer current price. */
    public static JSONObject record(JSONObject db,JSONObject food,String source,String effectiveDate,
            double price,double quantity,String unit,String supplier,String note)throws Exception {
        if(!ACTUAL.equals(source)&&!HQ.equals(source))throw new IllegalArgumentException("未知成本來源");
        LocalDate date=LocalDate.parse(effectiveDate);
        if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("價格生效日不能是未來日期");
        if(!Double.isFinite(price)||price<0||!Double.isFinite(quantity)||quantity<=0)throw new IllegalArgumentException("價格與數量不正確");
        if(CostMath.toBase(quantity,unit)<=0)throw new IllegalArgumentException("數量單位不正確");
        List<JSONObject> before=records(db,food.optString("id"),source);
        String latest=before.isEmpty()?"":before.get(before.size()-1).optString("date");
        JSONObject entry=new JSONObject().put("id",UUID.randomUUID().toString())
            .put("ingredientId",food.getString("id")).put("source",source)
            .put("date",effectiveDate).put("recordedAt",LocalDateTime.now().toString())
            .put("price",price).put("packQty",quantity).put("unit",unit)
            .put("supplier",supplier==null?"":supplier).put("note",note==null?"":note);
        db.getJSONArray("purchases").put(entry);
        if(latest.isEmpty()||effectiveDate.compareTo(latest)>=0){
            if(isHq(source)){
                food.put("hqPrice",price).put("hqPackQty",quantity).put("hqUnit",unit).put("hqSupplier",supplier);
            } else {
                food.put("price",price).put("packQty",quantity).put("unit",unit).put("supplier",supplier);
            }
        }
        return entry;
    }
    public static Double previousUnitCost(List<JSONObject> history,int index){
        if(index<=0)return null;
        JSONObject p=history.get(index-1);
        double qty=CostMath.toBase(p.optDouble("packQty",0),p.optString("unit"));
        return qty>0?p.optDouble("price",0)/qty:null;
    }
    /** Cost effect per one serving; callers should pass the unit-normalized before/after. */
    public static double impact(JSONObject recipe,String ingredientId,double unitDelta){
        JSONArray lines=recipe.optJSONArray("lines");double total=0;
        if(lines!=null)for(int i=0;i<lines.length();i++){
            JSONObject line=lines.optJSONObject(i);
            if(line!=null&&ingredientId.equals(line.optString("ingredientId")))total+=line.optDouble("qty")*unitDelta;
        }
        return total;
    }
}
