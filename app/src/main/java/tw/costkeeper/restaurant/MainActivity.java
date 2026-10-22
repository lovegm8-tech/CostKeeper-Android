package tw.costkeeper.restaurant;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/** Offline restaurant food-cost app, no external services or third-party SDK. */
public class MainActivity extends Activity {
    private static final int BG=Color.rgb(246,248,250), DARK=Color.rgb(29,46,55), GREEN=Color.rgb(12,117,106), MUTED=Color.rgb(101,117,128);
    private static final int BACKUP_WRITE=221, BACKUP_READ=222, CSV_READ=223, XLSX_WRITE=224, XLSX_READ=225;
    private static final String[] UNITS={"克","公斤","毫升","公升","顆","片","個","包","罐","盒","杯","份","支","張","條","塊","隻","桶","袋"};
    private static final String[] CATEGORIES={"米飯","食用油","冷凍與主食","肉類","海鮮","蛋奶","蔬菜","醬料","飲品","乾貨","包材","其他"};
    private static final String[] CHANNELS={"店內／外帶","Uber","Foodpanda"};
    private SecureStore store;
    private JSONObject db;
    private LinearLayout root,content;
    private String page="home";
    private YearMonth month=YearMonth.now();
    private char[] backupPassword;
    private boolean blocked=false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(GREEN);
        getWindow().setNavigationBarColor(Color.rgb(231,238,238));
        store=new SecureStore(this);
        try { db=store.load(); } catch(Exception e) {
            blocked=true; db=SecureStore.blank();
            new AlertDialog.Builder(this).setTitle("無法讀取已加密資料")
                .setMessage("為避免覆蓋原本的成本資料，App 已停止儲存。請確認裝置或使用先前加密備份還原。\n\n"+e.getClass().getSimpleName())
                .setPositiveButton("了解",(d,w)->{}).show();
        }
        render();
    }
    private void commit() {
        if(blocked){ toast("資料處於保護狀態，無法寫入"); return; }
        try { store.save(db); render(); }
        catch(Exception e){ new AlertDialog.Builder(this).setTitle("儲存失敗")
             .setMessage("本次變更未成功保存："+e.getMessage()).setPositiveButton("知道了",null).show(); }
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(float d){return (int)(getResources().getDisplayMetrics().density*d+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private TextView text(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(DARK);v.setTextSize(sp); if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private TextView note(String s){TextView v=text(s,12,false);v.setTextColor(MUTED);return v;}
    private Button button(String s,Runnable action) {
        Button b=new Button(this);b.setText(s);b.setTextAllCaps(false);b.setTextColor(GREEN);b.setOnClickListener(v->action.run());return b;
    }
    private void space(LinearLayout l,int height){ View v=new View(this); l.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    private void heading(LinearLayout l,String s){TextView v=text(s,18,true);pad(v,0,12);l.addView(v);}
    private void bar(LinearLayout l,String label,Runnable action){
        TextView v=text(label+"    ›",15,false);pad(v,12,13);v.setBackgroundColor(Color.WHITE);l.addView(v, new LinearLayout.LayoutParams(-1,-2));v.setOnClickListener(x->action.run());space(l,5);
    }
    private String money(double x){return String.format(Locale.TAIWAN,"NT$%,.2f",x);}
    private String num(double x){return String.format(Locale.TAIWAN,"%.3f",x).replaceAll("0+$","").replaceAll("\\.$","");}
    private String newId(){return UUID.randomUUID().toString();}
    private JSONArray items(){return db.optJSONArray("ingredients");}
    private JSONArray recipes(){return db.optJSONArray("recipes");}
    private JSONArray sales(){return db.optJSONArray("sales");}
    private JSONArray purchases(){return db.optJSONArray("purchases");}
    private JSONObject findFood(String id){return CostMath.find(items(),id);}
    private JSONObject findRecipe(String id){return CostMath.find(recipes(),id);}
    private EditText edit(String hint,String value,boolean number){
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setHint(hint);e.setText(value==null?"":value);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED : InputType.TYPE_CLASS_TEXT);
        return e;
    }
    private double value(EditText e){ try{return Double.parseDouble(e.getText().toString().trim());}catch(Exception ex){return 0;}}
    private String valueText(EditText e){return e.getText().toString().trim();}
    private Spinner choices(String[] options,String selected){
        Spinner v=new Spinner(this);v.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
        for(int i=0;i<options.length;i++) if(options[i].equals(selected))v.setSelection(i);
        return v;
    }
    private LinearLayout section(){LinearLayout s=column();pad(s,12,10);s.setBackgroundColor(Color.WHITE);content.addView(s,new LinearLayout.LayoutParams(-1,-2));space(content,10);return s;}
    private void render(){
        root=column();root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=35){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{
                Insets sys=insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(sys.left,sys.top,sys.right,sys.bottom);
                return insets;
            });
        }
        setContentView(root);
        LinearLayout title=row();pad(title,14,12);title.setBackgroundColor(GREEN);
        TextView app=text("餐飲成本管家",21,true);app.setTextColor(Color.WHITE);
        title.addView(app,new LinearLayout.LayoutParams(0,-2,1));
        TextView offline=text("離線 · v1.0.1",12,false);offline.setTextColor(Color.WHITE);title.addView(offline);root.addView(title);
        LinearLayout nav=row();nav.setBackgroundColor(Color.WHITE);
        for(String[] item:new String[][]{{"首頁","home"},{"食材","ingredients"},{"餐點","recipes"},{"報表","reports"},{"備份","backup"}}){
            TextView n=text(item[0],13,item[1].equals(page));n.setGravity(Gravity.CENTER);n.setTextColor(item[1].equals(page)?GREEN:DARK);pad(n,2,15);
            nav.addView(n,new LinearLayout.LayoutParams(0,-2,1));n.setOnClickListener(v->{page=item[1];render();});
        }
        root.addView(nav);
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        content=column();pad(content,12,10);sc.addView(content);
        if(blocked){LinearLayout s=section();heading(s,"資料需要修復");s.addView(note("已停止儲存，防止覆蓋既有資料。可前往備份頁匯入加密備份。"));}
        switch(page){case "ingredients":drawIngredients();break;case "recipes":drawRecipes();break;case "reports":drawReports();break;case "backup":drawBackup();break;default:drawHome();}
    }
    private void drawHome(){
        LinearLayout s=section();heading(s,"營運概覽");
        s.addView(note("此處的餐點成本由最新進價計算，銷售報表則保存交易當時的成本快照。"));
        space(s,10);s.addView(text("食材 " +items().length()+" 項   ·   餐點 " +recipes().length()+" 道",17,true));
        double total=0,fees=0,cost=0;int qty=0;
        for(int i=0;i<sales().length();i++){JSONObject x=sales().optJSONObject(i);if(x==null||!x.optString("date").startsWith(month.toString()))continue;
            total+=x.optDouble("gross",0);fees+=x.optDouble("fees",0);cost+=x.optDouble("cost",0);qty+=x.optInt("qty",0);}
        heading(s,"本月銷售紀錄 · "+month);
        s.addView(text("已登記 "+qty+" 份     收入 "+money(total),14,false));
        s.addView(text("食材／包材成本 "+money(cost),14,false));
        s.addView(text("扣平台費後毛利 "+money(total-fees-cost),17,true));
        s.addView(note("不包含房租、人事、水電、稅金及未輸入的營業費用。"));
        LinearLayout x=section();heading(x,"快速操作");bar(x,"＋ 新增食材",()->showIngredientEditor(null));
        bar(x,"＋ 新增餐點",()->showRecipeEditor(null));bar(x,"＋ 登記銷售",this::showSaleEditor);
        LinearLayout r=section();heading(r,"餐點成本預覽");
        if(recipes().length()==0)r.addView(note("尚未建立餐點，請先新增食材後建立配方。"));
        for(int i=0;i<Math.min(8,recipes().length());i++){
            JSONObject f=recipes().optJSONObject(i);if(f==null)continue;
            double c=CostMath.recipeCost(f,db),p=f.optDouble("price",0);
            bar(r,f.optString("name")+" · 成本 "+money(c)+" · 毛利 "+money(p-c),()->showRecipeDetail(f.optString("id")));
        }
    }
    private void drawIngredients(){
        LinearLayout top=section();heading(top,"食材與包材");top.addView(note("整包進貨量與進價，自動換算每克、毫升或每件成本。"));
        top.addView(button("＋ 新增食材",()->showIngredientEditor(null)));
        EditText search=edit("搜尋食材名稱、分類或供應商", "",false);top.addView(search);
        LinearLayout found=column();content.addView(found);
        Runnable refill=()->{
            found.removeAllViews();String query=valueText(search).toLowerCase(Locale.ROOT);int count=0;
            for(int i=0;i<items().length();i++){
                JSONObject f=items().optJSONObject(i);if(f==null)continue;
                String searchable=f.optString("name")+" "+f.optString("category")+" "+f.optString("supplier");
                if(!searchable.toLowerCase(Locale.ROOT).contains(query))continue;count++;
                LinearLayout card=column();pad(card,12,10);card.setBackgroundColor(Color.WHITE);
                found.addView(card,new LinearLayout.LayoutParams(-1,-2));space(found,8);
                card.addView(text(f.optString("name"),16,true));
                card.addView(note(f.optString("category","其他")+" · "+num(f.optDouble("packQty"))+" "+f.optString("unit")+"／批 · "+f.optString("supplier","")));
                card.addView(text("進價 "+money(f.optDouble("price"))+"   ｜   每"+CostMath.baseUnit(f.optString("unit"))+" "+money(CostMath.unitCost(f)),14,false));
                card.addView(button("編輯 / 更新進價",()->showIngredientEditor(f.optString("id"))));
            }
            if(count==0)found.addView(note(items().length()==0?"尚未建立食材。":"沒有符合的食材。"));
        };
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int c,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){refill.run();}
            @Override public void afterTextChanged(Editable s){}
        });
        refill.run();
    }
    private void showIngredientEditor(String id){
        JSONObject old=id==null?null:findFood(id);
        LinearLayout form=column();pad(form,16,8);
        EditText name=edit("食材名稱",old==null?"":old.optString("name"),false);form.addView(name);
        Spinner category=choices(CATEGORIES,old==null?"其他":old.optString("category","其他"));form.addView(category);
        EditText pack=edit("整批進貨數量（如 30）",old==null?"":num(old.optDouble("packQty")),true);form.addView(pack);
        Spinner unit=choices(UNITS,old==null?"克":old.optString("unit","克"));form.addView(unit);
        EditText price=edit("整批進貨價格（元）",old==null?"":num(old.optDouble("price")),true);form.addView(price);
        EditText supplier=edit("供應商（可空白）",old==null?"":old.optString("supplier"),false);form.addView(supplier);
        new AlertDialog.Builder(this).setTitle(id==null?"新增食材":"編輯食材／更新進價").setView(form)
            .setPositiveButton("儲存",(d,w)->{
                String n=valueText(name);double q=value(pack),p=value(price);
                if(n.isEmpty()||q<=0||p<0){toast("請輸入食材名稱、正確進貨量與價格");return;}
                try{
                    JSONObject x=old==null?new JSONObject():old;
                    if(old==null)x.put("id",newId());
                    double before=old==null?Double.NaN:old.optDouble("price",Double.NaN);
                    double beforeQty=old==null?Double.NaN:old.optDouble("packQty",Double.NaN);
                    String beforeUnit=old==null?"":old.optString("unit");
                    x.put("name",n).put("category",category.getSelectedItem().toString())
                        .put("packQty",q).put("unit",unit.getSelectedItem().toString())
                        .put("price",p).put("supplier",valueText(supplier));
                    CostMath.upsert(items(),x);
                    if(old==null||Double.compare(before,p)!=0||Double.compare(beforeQty,q)!=0||!beforeUnit.equals(unit.getSelectedItem().toString())){
                        JSONObject entry=new JSONObject();entry.put("id",newId()).put("ingredientId",x.getString("id"))
                           .put("date",LocalDate.now().toString()).put("price",p).put("packQty",q)
                           .put("unit",unit.getSelectedItem().toString()).put("supplier",valueText(supplier));
                        purchases().put(entry);
                    }
                    commit();
                }catch(Exception e){toast("無法儲存食材："+e.getMessage());}
            })
            .setNeutralButton(old==null?"取消":"刪除",(d,w)->{if(old!=null)confirmDeleteIngredient(id);})
            .setNegativeButton("返回",null).show();
    }
    private void confirmDeleteIngredient(String id){
        for(int i=0;i<recipes().length();i++){
            JSONArray lines=recipes().optJSONObject(i).optJSONArray("lines");
            if(lines==null)continue;
            for(int j=0;j<lines.length();j++)if(id.equals(lines.optJSONObject(j).optString("ingredientId"))){
                toast("此食材仍被餐點使用，請先從配方移除，避免造成成本遺漏");return;
            }
        }
        new AlertDialog.Builder(this).setTitle("確認刪除食材").setMessage("歷史銷售紀錄會保留成本快照。")
            .setPositiveButton("刪除",(d,w)->{CostMath.remove(items(),id);commit();}).setNegativeButton("取消",null).show();
    }
    private void drawRecipes(){
        LinearLayout s=section();heading(s,"餐點配方");s.addView(note("每份配方可使用多個食材。成本會依最新食材進價同步更新。"));
        s.addView(button("＋ 新增餐點",()->showRecipeEditor(null)));
        for(int i=0;i<recipes().length();i++){
            JSONObject r=recipes().optJSONObject(i);if(r==null)continue;
            double cost=CostMath.recipeCost(r,db),price=r.optDouble("price",0);
            LinearLayout card=section();card.addView(text(r.optString("name"),17,true));
            card.addView(text("成本 "+money(cost)+"  ｜  店內售價 "+money(price),14,false));
            if(price>0)card.addView(note("店內毛利率 "+String.format(Locale.TAIWAN,"%.1f%%",(price-cost)/price*100.0)));
            card.addView(button("檢視 / 編輯配方",()->showRecipeDetail(r.optString("id"))));
        }
    }
    private void showRecipeDetail(String id){
        JSONObject recipe=findRecipe(id);if(recipe==null)return;
        double cost=CostMath.recipeCost(recipe,db);
        StringBuilder sb=new StringBuilder("每份原料及包材成本："+money(cost)+"\n");
        JSONArray lines=recipe.optJSONArray("lines");
        if(lines!=null)for(int i=0;i<lines.length();i++){
            JSONObject line=lines.optJSONObject(i);JSONObject food=findFood(line.optString("ingredientId"));if(food==null)continue;
            sb.append("\n").append(food.optString("name")).append(" ").append(num(line.optDouble("qty"))).append(" ")
                .append(CostMath.baseUnit(food.optString("unit"))).append("：").append(money(CostMath.unitCost(food)*line.optDouble("qty")));
        }
        for(String c:CHANNELS){double price=CostMath.salePrice(recipe,c),profit=CostMath.netProfit(recipe,db,c);
            sb.append("\n\n").append(c).append("售價 ").append(money(price)).append(" → 毛利 ").append(money(profit));
        }
        new AlertDialog.Builder(this).setTitle(recipe.optString("name"))
            .setMessage(sb.toString()).setPositiveButton("編輯",(d,w)->showRecipeEditor(id))
            .setNeutralButton("刪除",(d,w)->new AlertDialog.Builder(this).setTitle("刪除餐點？")
                .setMessage("歷史銷售紀錄仍會保留。")
                .setPositiveButton("刪除",(dd,ww)->{CostMath.remove(recipes(),id);commit();})
                .setNegativeButton("取消",null).show())
            .setNegativeButton("返回",null).show();
    }
    private static class RecipeLine {
        Spinner ingredient;EditText qty;TextView unit;
    }
    private void showRecipeEditor(String id){
        if(items().length()==0){toast("請先建立至少一項食材");return;}
        JSONObject old=id==null?null:findRecipe(id);
        ScrollView sc=new ScrollView(this);LinearLayout form=column();pad(form,14,8);sc.addView(form);
        EditText name=edit("餐點名稱",old==null?"":old.optString("name"),false);form.addView(name);
        form.addView(note("店內／外帶價格"));EditText price=edit("元",old==null?"":num(old.optDouble("price")),true);form.addView(price);
        form.addView(note("Uber 售價 / 平台抽成 %"));
        EditText uberPrice=edit("Uber 售價",old==null?"":num(old.optDouble("uberPrice")),true);form.addView(uberPrice);
        EditText uberFee=edit("Uber 抽成 %（如 35）",old==null?"35":num(old.optDouble("uberFee",35)),true);form.addView(uberFee);
        form.addView(note("Foodpanda 售價 / 平台抽成 %"));
        EditText pandaPrice=edit("Foodpanda 售價",old==null?"":num(old.optDouble("pandaPrice")),true);form.addView(pandaPrice);
        EditText pandaFee=edit("Foodpanda 抽成 %（如 35）",old==null?"35":num(old.optDouble("pandaFee",35)),true);form.addView(pandaFee);
        heading(form,"每份配方用量");
        form.addView(note("食材使用量請輸入標準單位：克、毫升或單個數量。"));
        LinearLayout linesBox=column();form.addView(linesBox);ArrayList<RecipeLine> rows=new ArrayList<>();
        JSONArray oldLines=old==null?new JSONArray():old.optJSONArray("lines");
        if(oldLines!=null)for(int i=0;i<oldLines.length();i++){
            JSONObject li=oldLines.optJSONObject(i);if(li!=null)addRecipeLine(linesBox,rows,li.optString("ingredientId"),li.optDouble("qty"));
        }
        form.addView(button("＋ 加入一項食材／包材",()->addRecipeLine(linesBox,rows,null,0)));
        new AlertDialog.Builder(this).setTitle(id==null?"新增餐點":"編輯餐點配方")
            .setView(sc).setPositiveButton("儲存配方",(d,w)->{
                double p=value(price),up=value(uberPrice),pp=value(pandaPrice),uf=value(uberFee),pf=value(pandaFee);
                if(valueText(name).isEmpty()||p<0||up<0||pp<0||uf<0||uf>100||pf<0||pf>100){toast("請檢查餐點名稱、售價與抽成（0～100）");return;}
                try{
                    if(rows.isEmpty()){toast("至少需要加入一項食材或包材");return;}
                    JSONArray lines=new JSONArray();
                    for(RecipeLine item:rows){
                        String foodId=items().optJSONObject(item.ingredient.getSelectedItemPosition()).optString("id");
                        double q=value(item.qty);if(q<=0){toast("配方用量必須大於零");return;}
                        lines.put(new JSONObject().put("ingredientId",foodId).put("qty",q));
                    }
                    JSONObject r=old==null?new JSONObject():old;
                    if(old==null)r.put("id",newId());
                    r.put("name",valueText(name)).put("price",p).put("uberPrice",up).put("uberFee",uf)
                        .put("pandaPrice",pp).put("pandaFee",pf).put("lines",lines);
                    CostMath.upsert(recipes(),r);commit();
                }catch(Exception ex){toast("無法儲存配方："+ex.getMessage());}
            }).setNegativeButton("取消",null).show();
    }
    private void addRecipeLine(LinearLayout box,ArrayList<RecipeLine> rows,String selectedId,double quantity){
        RecipeLine line=new RecipeLine();
        String[] names=new String[items().length()];int selected=0;
        for(int i=0;i<items().length();i++){JSONObject f=items().optJSONObject(i);names[i]=f.optString("name");if(f.optString("id").equals(selectedId))selected=i;}
        LinearLayout container=column();pad(container,4,7);
        line.ingredient=choices(names,names[selected]);line.ingredient.setSelection(selected);container.addView(line.ingredient);
        LinearLayout controls=row();line.qty=edit("每份使用量",quantity<=0?"":num(quantity),true);
        controls.addView(line.qty,new LinearLayout.LayoutParams(0,-2,1));
        line.unit=note(CostMath.baseUnit(items().optJSONObject(selected).optString("unit")));
        controls.addView(line.unit);Button remove=button("移除",()->{box.removeView(container);rows.remove(line);});controls.addView(remove);
        container.addView(controls);box.addView(container);rows.add(line);
        line.ingredient.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View v,int index,long z){
                line.unit.setText(CostMath.baseUnit(items().optJSONObject(index).optString("unit")));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
    }
    private void showSaleEditor(){
        if(recipes().length()==0){toast("請先建立餐點配方");return;}
        String[] recipeNames=new String[recipes().length()];for(int i=0;i<recipes().length();i++)recipeNames[i]=recipes().optJSONObject(i).optString("name");
        LinearLayout form=column();pad(form,18,12);form.addView(note("選擇餐點"));Spinner recipeSpin=choices(recipeNames,recipeNames[0]);form.addView(recipeSpin);
        form.addView(note("銷售管道"));Spinner channel=choices(CHANNELS,CHANNELS[0]);form.addView(channel);
        EditText qty=edit("銷售份數", "1",true);form.addView(qty);
        LocalDate[] soldDate={LocalDate.now()};Button date=button("日期："+soldDate[0],()->{});
        date.setOnClickListener(v->{LocalDate x=soldDate[0];new DatePickerDialog(this,(picker,y,m,day)->{
            soldDate[0]=LocalDate.of(y,m+1,day);date.setText("日期："+soldDate[0]);
        },x.getYear(),x.getMonthValue()-1,x.getDayOfMonth()).show();});form.addView(date);
        form.addView(note("記錄當次售價、抽成與成本快照。日後進貨漲價不會改寫歷史報表。"));
        new AlertDialog.Builder(this).setTitle("登記銷售").setView(form)
            .setPositiveButton("記錄",(d,w)->{
                double raw=value(qty);int n=(int)raw;
                if(n<=0||n!=raw||n>100000){toast("請填寫有效的整數份數");return;}
                try{
                    JSONObject rec=recipes().optJSONObject(recipeSpin.getSelectedItemPosition());String c=channel.getSelectedItem().toString();
                    double p=CostMath.salePrice(rec,c),fee=CostMath.feeRate(rec,c);
                    double cost=CostMath.recipeCost(rec,db);
                    if(p<=0){toast("所選管道尚未設定售價，請先編輯餐點");return;}
                    JSONObject x=new JSONObject().put("id",newId()).put("date",soldDate[0].toString())
                        .put("recipeId",rec.optString("id")).put("recipeName",rec.optString("name"))
                        .put("channel",c).put("qty",n).put("gross",p*n).put("fees",p*n*fee)
                        .put("cost",cost*n);
                    sales().put(x);commit();
                }catch(Exception e){toast("無法記錄銷售："+e.getMessage());}
            }).setNegativeButton("取消",null).show();
    }
    private void drawReports(){
        LinearLayout s=section();heading(s,"每月銷售與成本");
        LinearLayout switcher=row();switcher.addView(button("‹ 上月",()->{month=month.minusMonths(1);render();}));
        TextView m=text(month.toString(),17,true);m.setGravity(Gravity.CENTER);switcher.addView(m,new LinearLayout.LayoutParams(0,-2,1));
        switcher.addView(button("下月 ›",()->{month=month.plusMonths(1);render();}));s.addView(switcher);
        double gross=0,fee=0,cost=0;int count=0;
        for(int i=0;i<sales().length();i++){
            JSONObject x=sales().optJSONObject(i);if(x==null||!x.optString("date").startsWith(month.toString()))continue;
            gross+=x.optDouble("gross");fee+=x.optDouble("fees");cost+=x.optDouble("cost");count+=x.optInt("qty");
        }
        s.addView(text("銷售份數："+count,15,false));s.addView(text("營業額："+money(gross),15,false));
        s.addView(text("食材與包材："+money(cost),15,false));s.addView(text("平台抽成："+money(fee),15,false));
        heading(s,"估算毛利："+money(gross-fee-cost));
        s.addView(note("毛利不等於淨利。未計入租金、人事、水電、營業稅、平台其他費用、損耗及未登記銷售。"));
        s.addView(button("＋ 登記一筆銷售",this::showSaleEditor));
        LinearLayout log=section();heading(log,"本月銷售紀錄");
        for(int i=sales().length()-1;i>=0;i--){JSONObject x=sales().optJSONObject(i);if(x==null||!x.optString("date").startsWith(month.toString()))continue;
            String id=x.optString("id");
            bar(log,x.optString("date")+" · "+x.optString("recipeName")+" ×"+x.optInt("qty")+"（"+x.optString("channel")+"）",()->{
                new AlertDialog.Builder(this).setTitle("刪除銷售紀錄？")
                    .setMessage("此動作會重新計算月報。")
                    .setPositiveButton("刪除",(d,w)->{CostMath.remove(sales(),id);commit();})
                    .setNegativeButton("取消",null).show();
            });
        }
        LinearLayout p=section();heading(p,"近期食材進價更新");
        int shown=0;for(int i=purchases().length()-1;i>=0&&shown<15;i--){JSONObject x=purchases().optJSONObject(i);if(x==null)continue;
            JSONObject food=findFood(x.optString("ingredientId"));
            p.addView(note(x.optString("date")+"  "+(food==null?"已刪除食材":food.optString("name"))+"   "+money(x.optDouble("price"))));shown++;
        }
    }
    private void drawBackup(){
        LinearLayout s=section();heading(s,"離線資料安全");
        s.addView(note("所有食材、配方、價格與銷售紀錄保存在這支手機的 App 私有加密儲存區。此 App 不要求網路權限，也不會上傳成本資料。"));
        s.addView(button("匯出密碼加密備份",()->askBackupPassword(false)));
        s.addView(button("從加密備份還原",()->askBackupPassword(true)));
        LinearLayout excel=section();heading(excel,"Excel 完整備份 / 換手機");
        excel.addView(note("將食材、配方、進價歷史、銷售明細全部存成 Excel (.xlsx)，可直接用 Excel 開啟，也可在新手機完整還原。"));
        excel.addView(button("↓ 匯出完整 Excel 試算表",()->{
            Intent f=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            f.addCategory(Intent.CATEGORY_OPENABLE);
            f.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            f.putExtra(Intent.EXTRA_TITLE,"餐飲成本管家-完整備份-"+LocalDate.now()+".xlsx");
            startActivityForResult(f,XLSX_WRITE);
        }));
        excel.addView(button("↑ 從完整 Excel 還原（換手機）",()->{
            Intent f=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            f.addCategory(Intent.CATEGORY_OPENABLE);
            f.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            startActivityForResult(f,XLSX_READ);
        }));
        excel.addView(note("Excel 為明文，包含進貨成本與毛利。請勿傳到公開群組或無保護的共享資料夾。還原時會先檢查工作表與資料關聯，確認後才覆蓋本機資料。"));
        space(s,8);s.addView(note("重要：解除安裝或手機遺失會失去本機資料，請定期匯出備份並記住密碼。"));
        LinearLayout csv=section();heading(csv,"外部試算表資料匯入");
        csv.addView(note("可匯入 UTF-8 CSV，表頭：食材名稱,分類,進貨數量,單位,進貨總價,供應商。一般 CSV 是明文，僅在可信裝置存放。"));
        csv.addView(button("匯入食材 CSV",()->{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick,CSV_READ);
        }));
        LinearLayout about=section();heading(about,"版本與更新");
        about.addView(text("版本 v1.0.1  ·  tw.costkeeper.restaurant",14,false));
        about.addView(note("未來用同一套件名稱及固定正式簽章、遞增版本號安裝新版 APK，即可保留本機資料。"));
        about.addView(note("單機版本：不支援多手機同步、雲端備份與自動更新。"));
    }
    private void askBackupPassword(boolean restore){
        EditText password=edit("備份密碼至少 8 碼", "",false);
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout wrap=column();pad(wrap,18,12);wrap.addView(password);
        new AlertDialog.Builder(this).setTitle(restore?"輸入備份密碼":"設定本次備份密碼")
            .setView(wrap).setPositiveButton("繼續",(d,w)->{
                char[] pass=valueText(password).toCharArray();
                if(pass.length<8){Arrays.fill(pass,'\0');toast("密碼至少需 8 碼");return;}
                clearPassword();backupPassword=pass;
                Intent f=new Intent(restore?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT);
                f.addCategory(Intent.CATEGORY_OPENABLE);f.setType("application/octet-stream");
                if(!restore)f.putExtra(Intent.EXTRA_TITLE,"costkeeper-"+LocalDate.now()+".costbackup");
                startActivityForResult(f,restore?BACKUP_READ:BACKUP_WRITE);
            }).setNegativeButton("取消",null).show();
    }
    private void clearPassword(){if(backupPassword!=null){Arrays.fill(backupPassword,'\0');backupPassword=null;}}
    @Override protected void onDestroy(){clearPassword();super.onDestroy();}
    private byte[] readBytes(Uri uri)throws Exception{
        try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IllegalArgumentException("無法開啟檔案");
            byte[] b=new byte[8192];int n;
            while((n=in.read(b))>=0){out.write(b,0,n);if(out.size()>30_000_000)throw new IllegalArgumentException("檔案過大");}
            return out.toByteArray();
        }
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(code!=RESULT_OK||data==null||data.getData()==null){clearPassword();return;}
        Uri uri=data.getData();
        try{
            if(request==XLSX_WRITE){
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                    if(out==null)throw new IllegalArgumentException("無法建立 Excel 檔案");
                    ExcelTransfer.exportTo(out,db);
                }
                toast("完整 Excel 備份已儲存，請妥善保管");
            }else if(request==XLSX_READ){
                JSONObject candidate=ExcelTransfer.importFrom(readBytes(uri));
                String summary="食材 "+candidate.getJSONArray("ingredients").length()+" 項、餐點 "+candidate.getJSONArray("recipes").length()
                    +" 道、進價紀錄 "+candidate.getJSONArray("purchases").length()+" 筆、銷售紀錄 "+candidate.getJSONArray("sales").length()+" 筆。";
                new AlertDialog.Builder(this).setTitle("確認從 Excel 完整還原？")
                    .setMessage(summary+"\n\n這會覆蓋目前手機內全部成本資料。建議先匯出現有備份。")
                    .setPositiveButton("確認覆蓋並還原",(d,w)->{
                        try{store.save(candidate);blocked=false;db=candidate;render();toast("Excel 完整資料已還原");}
                        catch(Exception ex){toast("還原失敗："+ex.getMessage());}
                    }).setNegativeButton("取消",null).show();
            }else if(request==BACKUP_WRITE){
                byte[] backup=store.makeBackup(db,backupPassword);
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                    if(out==null)throw new IllegalArgumentException("無法建立備份檔");out.write(backup);
                }
                toast("已建立加密備份");
            }else if(request==BACKUP_READ){
                JSONObject candidate=store.restoreBackup(readBytes(uri),backupPassword);
                new AlertDialog.Builder(this).setTitle("確定還原？")
                    .setMessage("這會覆蓋目前的食材、配方與銷售資料，請先備份現有資料。")
                    .setPositiveButton("覆蓋並還原",(d,w)->{
                        try{store.save(candidate);blocked=false;db=candidate;render();toast("資料已還原");}
                        catch(Exception ex){toast("還原失敗："+ex.getMessage());}
                    }).setNegativeButton("取消",null).show();
            }else if(request==CSV_READ){
                String csv=new String(readBytes(uri),StandardCharsets.UTF_8);
                JSONArray extracted=parseCsv(csv);
                new AlertDialog.Builder(this).setTitle("準備匯入 "+extracted.length()+" 項食材")
                    .setMessage("將新增食材，不覆蓋原有同名資料。匯入前請確認單位、價格和數量。")
                    .setPositiveButton("匯入",(d,w)->{
                        if(blocked){toast("資料保護狀態無法寫入");return;}
                        for(int i=0;i<extracted.length();i++){
                            JSONObject ingredient=extracted.optJSONObject(i);items().put(ingredient);
                            try{purchases().put(new JSONObject().put("id",newId()).put("ingredientId",ingredient.getString("id"))
                                .put("date",LocalDate.now().toString()).put("price",ingredient.optDouble("price"))
                                .put("packQty",ingredient.optDouble("packQty")).put("unit",ingredient.optString("unit"))
                                .put("supplier",ingredient.optString("supplier")));}
                            catch(Exception ignored){}
                        }commit();
                    }).setNegativeButton("取消",null).show();
            }
        }catch(Exception e){
            String detail=e.getMessage();
            if(detail==null||detail.isEmpty())detail=e.getClass().getSimpleName();
            if(detail.length()>130)detail=detail.substring(0,130);
            new AlertDialog.Builder(this).setTitle("檔案處理失敗")
                .setMessage("請檢查檔案格式、工作表內容或備份密碼。\n原因："+detail)
                .setPositiveButton("知道了",null).show();
        }
        finally{clearPassword();}
    }
    private JSONArray parseCsv(String csv)throws Exception{
        // CSV lexer: RFC-4180 quotes, doubled quotes, CRLF. Supports quoted newlines.
        ArrayList<ArrayList<String>> table=new ArrayList<>();ArrayList<String> record=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quote=false;
        if(csv.startsWith("\uFEFF"))csv=csv.substring(1);
        for(int i=0;i<csv.length();i++){
            char c=csv.charAt(i);
            if(c=='"'){
                if(quote&&i+1<csv.length()&&csv.charAt(i+1)=='"'){field.append('"');i++;}else quote=!quote;
            }else if(c==','&&!quote){record.add(field.toString());field.setLength(0);}
            else if((c=='\n'||c=='\r')&&!quote){
                if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;
                record.add(field.toString());field.setLength(0);
                if(record.size()>1)table.add(record);record=new ArrayList<>();
            }else field.append(c);
        }
        if(quote)throw new IllegalArgumentException("CSV 引號未配對");
        record.add(field.toString());if(record.size()>1)table.add(record);
        if(table.size()<2)throw new IllegalArgumentException("CSV 至少需要表頭與一筆資料");
        ArrayList<String> h=table.get(0);
        String[] required={"食材名稱","分類","進貨數量","單位","進貨總價","供應商"};int[] pos=new int[required.length];
        for(int i=0;i<required.length;i++){pos[i]=h.indexOf(required[i]);if(pos[i]<0)throw new IllegalArgumentException("缺少欄位："+required[i]);}
        JSONArray imported=new JSONArray();
        for(int i=1;i<table.size();i++){
            ArrayList<String> line=table.get(i);if(line.size()<h.size())continue;
            String name=line.get(pos[0]).trim();if(name.isEmpty())continue;
            double q=Double.parseDouble(line.get(pos[2]).trim());double price=Double.parseDouble(line.get(pos[4]).trim());
            String unit=line.get(pos[3]).trim();if(!Arrays.asList(UNITS).contains(unit)||q<=0||price<0)throw new IllegalArgumentException("第"+(i+1)+"列單位、數量或價格錯誤");
            JSONObject x=new JSONObject().put("id",newId()).put("name",name).put("category",line.get(pos[1]).trim())
                .put("packQty",q).put("unit",unit).put("price",price).put("supplier",line.get(pos[5]).trim());
            imported.put(x);
        }
        if(imported.length()==0)throw new IllegalArgumentException("沒有可匯入的有效食材");
        return imported;
    }
}
