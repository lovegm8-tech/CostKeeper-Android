package tw.costkeeper.restaurant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Full-fidelity, offline .xlsx export/import, using only built-in Java and Android APIs.
 * The readable workbook itself is the portable data format. No proprietary service.
 * Only a CostKeeper workbook with the required headers is accepted for FULL RESTORE.
 */
public final class ExcelTransfer {
    private ExcelTransfer() {}
    private static final String[] NAMES={"使用說明","食材","餐點","配方明細","進價紀錄","銷售紀錄"};
    private static final String[][] HEADERS={
        {"CostKeeperData","2"},
        {"ID","食材名稱","分類","進貨數量","單位","進貨總價","供應商","單位成本（參考）"},
        {"ID","餐點名稱","店內售價","Uber售價","Uber抽成率%","Foodpanda售價","Foodpanda抽成率%","配方成本（參考）","店內毛利（參考）"},
        {"餐點ID","食材ID","使用量（標準單位）","餐點名稱（參考）","食材名稱（參考）"},
        {"ID","食材ID","日期","進價","進貨數量","單位","供應商","食材名稱（參考）"},
        {"ID","日期","餐點ID","餐點名稱","通路","份數","銷售額","平台費","食材成本"}
    };
    private static final int MAX_FILE=30_000_000;
    private static final int MAX_ZIP_ENTRY=15_000_000;
    private static final int MAX_ROWS=100000;

    private static final class V {
        final String text; final boolean num;
        V(String value, boolean numeric){text=value==null?"":value;num=numeric;}
    }
    private static V s(String x){return new V(x,false);}
    private static V n(double d){return new V(Double.toString(d),true);}
    private static List<V> row(V... a){return Arrays.asList(a);}
    private static String xml(String s){
        StringBuilder o=new StringBuilder(s.length()+12);
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            switch(c){case '&':o.append("&amp;");break;case '<':o.append("&lt;");break;
            case '>':o.append("&gt;");break;case '"':o.append("&quot;");break;
            default:if(c>=32||c=='\t'||c=='\n'||c=='\r')o.append(c);}
        }
        return o.toString();
    }
    private static String col(int i){
        StringBuilder b=new StringBuilder();i++;
        while(i>0){i--;b.insert(0,(char)('A'+i%26));i/=26;}
        return b.toString();
    }
    private static void entry(ZipOutputStream out,String path,String content)throws Exception{
        out.putNextEntry(new ZipEntry(path));out.write(content.getBytes(StandardCharsets.UTF_8));out.closeEntry();
    }
    private static String sheetXml(List<List<V>> table){
        StringBuilder b=new StringBuilder(4096);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
         .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
         .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
         .append("<sheetFormatPr defaultRowHeight=\"18\"/>")
         .append("<cols><col min=\"1\" max=\"1\" width=\"38\" customWidth=\"1\"/>")
         .append("<col min=\"2\" max=\"2\" width=\"28\" customWidth=\"1\"/>")
         .append("<col min=\"3\" max=\"16\" width=\"20\" customWidth=\"1\"/></cols>")
         .append("<sheetData>");
        for(int y=0;y<table.size();y++){
            List<V> line=table.get(y);b.append("<row r=\"").append(y+1).append("\">");
            for(int x=0;x<line.size();x++){
                V v=line.get(x);String addr=col(x)+(y+1);
                if(v.num){b.append("<c r=\"").append(addr).append(y==0?"\" s=\"1\"><v>":"\"><v>").append(xml(v.text)).append("</v></c>");}
                else b.append("<c r=\"").append(addr).append(y==0?"\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">":"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(v.text)).append("</t></is></c>");
            }
            b.append("</row>");
        }
        return b.append("</sheetData></worksheet>").toString();
    }
    private static List<List<V>> table(int index,JSONObject db){
        List<List<V>> out=new ArrayList<>();
        List<V> header=new ArrayList<>();for(String h:HEADERS[index])header.add(s(h));out.add(header);
        if(index==0){
            out.add(row(s("格式版本"),s("2")));
            out.add(row(s("重要"),s("此 Excel 包含營業成本、配方、售價等明文商業資料。請妥善保存。")));
            out.add(row(s("換機步驟"),s("新手機安裝相同 App → 備份 → 從 Excel 還原 → 選取本檔 → 確認覆蓋。")));
            out.add(row(s("編輯提醒"),s("可修改食材進價、配方用量、售價。請勿修改 ID、欄位名稱或刪除工作表；參考欄不作還原依據。")));
            out.add(row(s("備份內容"),s("食材、餐點、配方明細、歷史進價與銷售快照。")));
            out.add(row(s("建議"),s("如有隱私要求，另請製作密碼加密備份。一般 Excel 本身不加密。")));
            return out;
        }
        JSONArray ingredients=db.optJSONArray("ingredients"),recipes=db.optJSONArray("recipes"),purchases=db.optJSONArray("purchases"),sales=db.optJSONArray("sales");
        switch(index){
            case 1:
                for(int i=0;i<ingredients.length();i++){
                    JSONObject x=ingredients.optJSONObject(i);if(x==null)continue;
                    out.add(row(s(x.optString("id")),s(x.optString("name")),s(x.optString("category")),
                        n(x.optDouble("packQty")),s(x.optString("unit")),n(x.optDouble("price")),s(x.optString("supplier")),n(CostMath.unitCost(x))));
                }break;
            case 2:
                for(int i=0;i<recipes.length();i++){
                    JSONObject x=recipes.optJSONObject(i);if(x==null)continue;
                    double cost=CostMath.recipeCost(x,db);
                    out.add(row(s(x.optString("id")),s(x.optString("name")),n(x.optDouble("price")),n(x.optDouble("uberPrice")),
                        n(x.optDouble("uberFee")),n(x.optDouble("pandaPrice")),n(x.optDouble("pandaFee")),n(cost),n(x.optDouble("price")-cost)));
                }break;
            case 3:
                for(int i=0;i<recipes.length();i++){
                    JSONObject r=recipes.optJSONObject(i);if(r==null)continue;
                    JSONArray lines=r.optJSONArray("lines");if(lines==null)continue;
                    for(int j=0;j<lines.length();j++){
                        JSONObject l=lines.optJSONObject(j);if(l==null)continue;
                        JSONObject food=CostMath.find(ingredients,l.optString("ingredientId"));
                        out.add(row(s(r.optString("id")),s(l.optString("ingredientId")),n(l.optDouble("qty")),
                            s(r.optString("name")),s(food==null?"已移除食材":food.optString("name"))));
                    }
                }break;
            case 4:
                for(int i=0;i<purchases.length();i++){
                    JSONObject x=purchases.optJSONObject(i);if(x==null)continue;
                    JSONObject food=CostMath.find(ingredients,x.optString("ingredientId"));
                    out.add(row(s(x.optString("id")),s(x.optString("ingredientId")),s(x.optString("date")),n(x.optDouble("price")),
                        n(x.optDouble("packQty")),s(x.optString("unit")),s(x.optString("supplier")),s(food==null?"歷史食材":food.optString("name"))));
                }break;
            case 5:
                for(int i=0;i<sales.length();i++){
                    JSONObject x=sales.optJSONObject(i);if(x==null)continue;
                    out.add(row(s(x.optString("id")),s(x.optString("date")),s(x.optString("recipeId")),s(x.optString("recipeName")),
                        s(x.optString("channel")),n(x.optInt("qty")),n(x.optDouble("gross")),n(x.optDouble("fees")),n(x.optDouble("cost"))));
                }break;
        }
        return out;
    }
    public static void exportTo(OutputStream target,JSONObject db)throws Exception{
        if(db.optInt("format")!=1)throw new IllegalArgumentException("資料庫版本不支援");
        ZipOutputStream zip=new ZipOutputStream(target);
        StringBuilder types=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for(int i=0;i<NAMES.length;i++)types.append("<Override PartName=\"/xl/worksheets/sheet").append(i+1).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        types.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
        entry(zip,"[Content_Types].xml",types.toString());
        entry(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        StringBuilder workbook=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        StringBuilder rels=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for(int i=0;i<NAMES.length;i++){
            workbook.append("<sheet name=\"").append(xml(NAMES[i])).append("\" sheetId=\"").append(i+1).append("\" r:id=\"rId").append(i+1).append("\"/>");
            rels.append("<Relationship Id=\"rId").append(i+1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i+1).append(".xml\"/>");
        }
        workbook.append("</sheets><calcPr calcId=\"0\"/></workbook>");
        rels.append("<Relationship Id=\"rId7\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
        entry(zip,"xl/workbook.xml",workbook.toString());entry(zip,"xl/_rels/workbook.xml.rels",rels.toString());
        entry(zip,"xl/styles.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0F766E\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/></cellXfs></styleSheet>");
        for(int i=0;i<NAMES.length;i++)entry(zip,"xl/worksheets/sheet"+(i+1)+".xml",sheetXml(table(i,db)));
        zip.finish();zip.flush();
    }

    private static SAXParserFactory factory()throws Exception{
        SAXParserFactory f=SAXParserFactory.newInstance();f.setNamespaceAware(false);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        return f;
    }
    private static void parse(byte[] xml,DefaultHandler handler)throws Exception{
        factory().newSAXParser().parse(new ByteArrayInputStream(xml),handler);
    }
    private static String bare(String q){int i=q.indexOf(':');return i<0?q:q.substring(i+1);}
    private static Map<String,byte[]> unzip(byte[] input)throws Exception{
        if(input.length>MAX_FILE)throw new IllegalArgumentException("Excel 檔案過大");
        Map<String,byte[]> data=new HashMap<>();int total=0,entryCount=0;
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(input))){ZipEntry e;
            while((e=z.getNextEntry())!=null){
                if(++entryCount>200)throw new IllegalArgumentException("Excel 內容項目過多");
                if(e.isDirectory())continue;
                String name=e.getName();if(!name.startsWith("xl/") && !name.equals("[Content_Types].xml"))continue;
                ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                while((n=z.read(buf))!=-1){b.write(buf,0,n);total+=n;
                    if(b.size()>MAX_ZIP_ENTRY||total>40_000_000)throw new IllegalArgumentException("Excel 解壓資料量過大");
                }
                data.put(name,b.toByteArray());z.closeEntry();
            }
        }
        return data;
    }
    private static List<String> sharedStrings(byte[] input)throws Exception{
        if(input==null)return new ArrayList<>();List<String> strings=new ArrayList<>();
        parse(input,new DefaultHandler(){StringBuilder str;boolean text=false;
            @Override public void startElement(String uri,String local,String q,Attributes a){String name=bare(q);if(name.equals("si"))str=new StringBuilder();if(name.equals("t"))text=true;}
            @Override public void characters(char[] ch,int st,int len){if(text&&str!=null)str.append(ch,st,len);}
            @Override public void endElement(String uri,String local,String q){String name=bare(q);if(name.equals("t"))text=false; if(name.equals("si"))strings.add(str.toString());}
        });return strings;
    }
    private static Map<String,String> relationshipPaths(Map<String,byte[]> files)throws Exception{
        Map<String,String> rels=new HashMap<>();byte[] b=files.get("xl/_rels/workbook.xml.rels");
        if(b==null)throw new IllegalArgumentException("缺少 Excel 工作表關聯");
        parse(b,new DefaultHandler(){
            @Override public void startElement(String uri,String local,String q,Attributes a){
                if(!bare(q).equals("Relationship"))return;
                String id=a.getValue("Id"),target=a.getValue("Target"),type=a.getValue("Type");
                if(id==null||target==null||type==null||!type.endsWith("/worksheet"))return;
                String path=target.startsWith("/")?target.substring(1):"xl/"+target;
                if(path.startsWith("xl/worksheets/")&&!path.contains(".."))rels.put(id,path);
            }
        });return rels;
    }
    private static Map<String,String> workbookSheets(byte[] xml,Map<String,String> rels)throws Exception{
        if(xml==null)throw new IllegalArgumentException("缺少 Excel 工作簿");
        Map<String,String> sheets=new HashMap<>();
        parse(xml,new DefaultHandler(){
            @Override public void startElement(String uri,String local,String q,Attributes a){
                if(!bare(q).equals("sheet"))return;
                String name=a.getValue("name"),id=a.getValue("r:id");
                if(name!=null&&id!=null&&rels.containsKey(id))sheets.put(name,rels.get(id));
            }
        });return sheets;
    }
    private static List<List<String>> rows(byte[] xml,List<String> shared)throws Exception{
        if(xml==null)throw new IllegalArgumentException("Excel 工作表遺失");
        List<List<String>> all=new ArrayList<>();
        parse(xml,new DefaultHandler(){
            List<String> line;StringBuilder cell;String kind,position;
            boolean reading=false;int currentCol=-1,lastRow=0;
            @Override public void startElement(String uri,String local,String q,Attributes a){
                String name=bare(q);
                if(name.equals("row")){
                    int row=lastRow+1;try{row=Integer.parseInt(a.getValue("r"));}catch(Exception ignored){}
                    if(row<=0||row>MAX_ROWS)throw new IllegalArgumentException("Excel 列數超出限制");
                    while(lastRow<row-1){all.add(new ArrayList<>());lastRow++;}
                    line=new ArrayList<>();lastRow=row;
                }else if(name.equals("c")&&line!=null){
                    kind=a.getValue("t");position=a.getValue("r");cell=new StringBuilder();currentCol=-1;
                    if(position!=null){int offset=0;while(offset<position.length()&&Character.isLetter(position.charAt(offset))){char c=Character.toUpperCase(position.charAt(offset++));currentCol=(currentCol+1)*26+(c-'A'+1)-1;}}
                    if(currentCol<0)currentCol=line.size();
                    if(currentCol>80)throw new IllegalArgumentException("Excel 欄位數過多");
                }else if((name.equals("v")||name.equals("t"))&&cell!=null)reading=true;
            }
            @Override public void characters(char[] ch,int st,int len){if(reading&&cell!=null)cell.append(ch,st,len);}
            @Override public void endElement(String uri,String local,String q){
                String name=bare(q);
                if(name.equals("v")||name.equals("t"))reading=false;
                if(name.equals("c")&&line!=null&&cell!=null){
                    String val=cell.toString();
                    if("s".equals(kind)){
                        try{int index=Integer.parseInt(val);val=shared.get(index);}catch(Exception e){throw new IllegalArgumentException("Excel 共用字串無效");}
                    }
                    while(line.size()<=currentCol)line.add("");line.set(currentCol,val);cell=null;
                }
                if(name.equals("row")&&line!=null){all.add(line);line=null;}
            }
        });return all;
    }
    private static String c(List<String> row,int idx){return idx<row.size()?row.get(idx).trim():"";}
    private static void headers(List<List<String>> all,int sheet)throws Exception{
        if(all.isEmpty())throw new IllegalArgumentException(NAMES[sheet]+" 無標頭");
        String[] keys=HEADERS[sheet];List<String> head=all.get(0);
        int required=sheet==0?2:(sheet==1?7:sheet==2?7:sheet==3?3:sheet==4?7:9);
        for(int i=0;i<required;i++)if(!keys[i].equals(c(head,i)))throw new IllegalArgumentException(NAMES[sheet]+" 的第"+(i+1)+"欄標頭被修改");
    }
    private static double d(String s,String field,boolean positive)throws Exception{
        double v;try{v=Double.parseDouble(s);}catch(Exception e){throw new IllegalArgumentException("數字無效："+field);}
        if(!Double.isFinite(v)||v<0||(positive&&v<=0)||v>1e12)throw new IllegalArgumentException("數字超出範圍："+field);
        return v;
    }
    private static String id(String text){return text.isEmpty()?UUID.randomUUID().toString():text;}
    private static String required(String value,String field){if(value.isEmpty())throw new IllegalArgumentException("缺少 "+field);return value;}
    private static void unique(Set<String> seen,String id,String sheet){if(!seen.add(id))throw new IllegalArgumentException(sheet+" 發現重複 ID");}
    private static List<String> allowedUnits=Arrays.asList("克","公斤","毫升","公升","顆","片","個","包","罐","盒","杯","份","支","張","條","塊","隻","桶","袋");
    private static String unit(String x){if(!allowedUnits.contains(x))throw new IllegalArgumentException("不支援單位："+x);return x;}
    private static boolean blank(List<String> row){for(String x:row)if(!x.trim().isEmpty())return false;return true;}
    private static String date(String text){try{return LocalDate.parse(text).toString();}catch(Exception ex){throw new IllegalArgumentException("日期應為 YYYY-MM-DD："+text);}}
    private static final class Counter {int ingredients,recipes,lines,purchases,sales;}
    public static JSONObject importFrom(byte[] input)throws Exception{
        Map<String,byte[]> zip=unzip(input);
        Map<String,String> paths=workbookSheets(zip.get("xl/workbook.xml"),relationshipPaths(zip));
        List<String> shared=sharedStrings(zip.get("xl/sharedStrings.xml"));
        List<List<List<String>>> sheets=new ArrayList<>();
        for(int i=0;i<NAMES.length;i++){
            String path=paths.get(NAMES[i]);if(path==null)throw new IllegalArgumentException("缺少「"+NAMES[i]+"」工作表");
            List<List<String>> r=rows(zip.get(path),shared);headers(r,i);sheets.add(r);
        }
        if(!"CostKeeperData".equals(c(sheets.get(0).get(0),0))||!"2".equals(c(sheets.get(0).get(0),1)))throw new IllegalArgumentException("不是成本管家完整 Excel 備份 v2");
        JSONObject db=SecureStore.blank();JSONArray ingredients=db.getJSONArray("ingredients"),recipes=db.getJSONArray("recipes"),purchases=db.getJSONArray("purchases"),sales=db.getJSONArray("sales");
        Set<String> foodIds=new HashSet<>(),recipeIds=new HashSet<>(),purchaseIds=new HashSet<>(),saleIds=new HashSet<>();
        for(List<String> r:sheets.get(1).subList(1,sheets.get(1).size())){
            if(blank(r))continue;
            String i=id(c(r,0)),name=required(c(r,1),"食材名稱"),un=unit(c(r,4));unique(foodIds,i,"食材");
            ingredients.put(new JSONObject().put("id",i).put("name",name).put("category",c(r,2)).put("packQty",d(c(r,3),"進貨數量",true))
                .put("unit",un).put("price",d(c(r,5),"進貨總價",false)).put("supplier",c(r,6)));
        }
        for(List<String> r:sheets.get(2).subList(1,sheets.get(2).size())){
            if(blank(r))continue;String i=id(c(r,0));unique(recipeIds,i,"餐點");
            double uberFee=d(c(r,4),"Uber抽成率",false),pandaFee=d(c(r,6),"Foodpanda抽成率",false);
            if(uberFee>100||pandaFee>100)throw new IllegalArgumentException("平台抽成率不得大於100%");
            recipes.put(new JSONObject().put("id",i).put("name",required(c(r,1),"餐點名稱"))
                .put("price",d(c(r,2),"店內售價",false)).put("uberPrice",d(c(r,3),"Uber售價",false))
                .put("uberFee",uberFee).put("pandaPrice",d(c(r,5),"Foodpanda售價",false))
                .put("pandaFee",pandaFee).put("lines",new JSONArray()));
        }
        for(List<String> r:sheets.get(3).subList(1,sheets.get(3).size())){
            if(blank(r))continue;
            JSONObject recipe=CostMath.find(recipes,c(r,0));
            if(recipe==null||!foodIds.contains(c(r,1)))throw new IllegalArgumentException("配方明細引用不存在的餐點或食材 ID");
            recipe.getJSONArray("lines").put(new JSONObject().put("ingredientId",c(r,1)).put("qty",d(c(r,2),"每份使用量",true)));
        }
        for(List<String> r:sheets.get(4).subList(1,sheets.get(4).size())){
            if(blank(r))continue;String i=id(c(r,0));unique(purchaseIds,i,"進價紀錄");
            purchases.put(new JSONObject().put("id",i).put("ingredientId",c(r,1)).put("date",date(c(r,2)))
                .put("price",d(c(r,3),"進價",false)).put("packQty",d(c(r,4),"進貨數量",true))
                .put("unit",unit(c(r,5))).put("supplier",c(r,6)));
        }
        for(List<String> r:sheets.get(5).subList(1,sheets.get(5).size())){
            if(blank(r))continue;String i=id(c(r,0));unique(saleIds,i,"銷售紀錄");
            double qty=d(c(r,5),"銷售份數",true);if(qty!=Math.rint(qty)||qty>10000000)throw new IllegalArgumentException("銷售份數必須是正整數");
            String channel=c(r,4);if(!Arrays.asList("店內／外帶","Uber","Foodpanda").contains(channel))throw new IllegalArgumentException("未知銷售通路："+channel);
            sales.put(new JSONObject().put("id",i).put("date",date(c(r,1))).put("recipeId",c(r,2))
                .put("recipeName",c(r,3)).put("channel",channel).put("qty",(int)qty)
                .put("gross",d(c(r,6),"銷售額",false)).put("fees",d(c(r,7),"平台費",false))
                .put("cost",d(c(r,8),"食材成本",false)));
        }
        return db;
    }
}
