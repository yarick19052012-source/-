package app.topka.nativejournal;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.*;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Journal extends Activity {
    private static final int INK=0xff23372e, GREEN=0xff285443, PAPER=0xfff7f5ee, WHITE=0xffffffff;
    private static final String[] DISH={"Овощной салат","Пицца","Завтрак с яйцом"};
    private static final String[] PHOTOS={"salad.jpg","pizza.jpg","breakfast.jpg"};
    private SQLiteDatabase db;
    private LinearLayout page;
    private LocalDate day;
    private int tab;
    private String pendingDay;
    private final Locale ru=new Locale("ru","RU");

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        db=openOrCreateDatabase("journal.db",MODE_PRIVATE,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS entry(id INTEGER PRIMARY KEY, day TEXT, kind TEXT, title TEXT, amount INTEGER, photo TEXT)");
        day=saved==null?LocalDate.now():LocalDate.parse(saved.getString("day"));
        if(saved!=null){tab=saved.getInt("tab");pendingDay=saved.getString("capture");}
        getWindow().setStatusBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("day",day.toString());b.putInt("tab",tab);b.putString("capture",pendingDay);}
    int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    void pad(View v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    TextView line(LinearLayout box,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(INK);box.addView(t);return t;}
    void heading(LinearLayout box,String name){TextView t=line(box,name,30);t.setTypeface(Typeface.SERIF,Typeface.BOLD);t.setPadding(0,dp(12),0,dp(8));}
    void button(LinearLayout box,String name,Runnable action){Button b=new Button(this);
        b.setTextSize(14);b.setMinHeight(dp(48));b.setElevation(0);b.setStateListAnimator(null);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x24285443),bg(0xffeaf0e9,14),null));
        b.setPadding(dp(8),dp(6),dp(8),dp(6));b.setAllCaps(false);b.setText(name);b.setTextColor(GREEN);b.setOnClickListener(v->action.run());boolean horizontal=box.getOrientation()==0;
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(horizontal?0:-1,-2,horizontal?1:0);
        lp.setMargins(0,dp(5),horizontal?dp(4):0,dp(5));box.addView(b,lp);}
    LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout(this);parent.addView(r);return r;}
    LinearLayout card(int color){LinearLayout c=column();pad(c,16);c.setBackground(bg(color,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(c,lp);return c;}
    int total(String kind){try(Cursor c=db.rawQuery("SELECT coalesce(sum(amount),0) FROM entry WHERE day=? AND kind=?",new String[]{day.toString(),kind})){c.moveToFirst();return c.getInt(0);}}
    void render(){
        LinearLayout root=column();root.setBackgroundColor(PAPER);ScrollView scroll=new ScrollView(this);page=column();pad(page,20);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String[] titles={"Мой день","Вода","История","Питание"};
        heading(page,titles[tab]);
        TextView date=line(page,day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",ru))+"   ▾",16);
        date.setTextColor(GREEN);date.setGravity(Gravity.CENTER_VERTICAL);date.setMinHeight(dp(48));
        date.setOnClickListener(v->new DatePickerDialog(this,(picker,y,m,d)->{day=LocalDate.of(y,m+1,d);render();},day.getYear(),day.getMonthValue()-1,day.getDayOfMonth()).show());
        switch(tab){
            case 0: overview();break;
            case 1: water();heading(page,"Записи воды");entries();break;
            case 2: entries();break;
            case 3: food();break;
        }
        LinearLayout nav=row(root);nav.setGravity(Gravity.CENTER);nav.setBackgroundColor(WHITE);pad(nav,6);
        addTab(nav,0,"День");addTab(nav,3,"Еда");addTab(nav,1,"Вода");addTab(nav,2,"История");
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(root);
    }
    void openTab(int index){tab=index;render();}
    void addTab(LinearLayout nav,int index,String label){
        boolean selected=tab==index;
        LinearLayout item=column();item.setGravity(Gravity.CENTER);pad(item,5);
        if(selected)item.setBackground(bg(0xffe5eee6,18));
        View symbol=new TabIcon(index);item.addView(symbol,new LinearLayout.LayoutParams(dp(24),dp(24)));
        TextView caption=line(item,label,12);caption.setTypeface(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);
        item.setSelected(selected);item.setFocusable(true);item.setContentDescription(label);
        item.setOnClickListener(v->openTab(index));
        nav.addView(item,new LinearLayout.LayoutParams(0,dp(60),1));
    }
    class TabIcon extends View {
        final int index;final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        TabIcon(int index){super(Journal.this);this.index=index;pen.setColor(GREEN);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.6f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);}
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(getWidth()/24f,getHeight()/24f);Path p=new Path();
            if(index==0){p.moveTo(3,11);p.lineTo(12,4);p.lineTo(21,11);p.moveTo(5,10);p.lineTo(5,20);p.lineTo(19,20);p.lineTo(19,10);}
            else if(index==1){p.moveTo(12,2);p.cubicTo(10,6,5,11,5,15);p.cubicTo(5,24,19,24,19,15);p.cubicTo(19,11,14,6,12,2);}
            else if(index==3){c.drawArc(2,4,22,20,0,180,false,pen);c.drawLine(2,12,22,12,pen);c.drawLine(9,20,15,20,pen);c.drawLine(10,8,12,4,pen);c.drawLine(15,8,17,4,pen);}
            else{c.drawCircle(12,12,8.5f,pen);p.moveTo(12,7);p.lineTo(12,12);p.lineTo(16,14);}
            c.drawPath(p,pen);c.restore();
        }
    }
    void overview(){
        LinearLayout hero=card(GREEN);
        TextView label=line(hero,"Питание за день",16);label.setTextColor(WHITE);
        TextView value=line(hero,total("meal")+" ккал",38);value.setTextColor(WHITE);value.setTypeface(Typeface.SERIF,Typeface.BOLD);
        photo(hero,PHOTOS[0]);hero.setContentDescription("Открыть питание, "+value.getText());hero.setOnClickListener(v->openTab(3));
        LinearLayout hydration=card(0xffdeedf0);line(hydration,"Вода",17);
        line(hydration,total("water")+" из "+getPreferences(0).getInt("goal",2000)+" мл",24);
        button(hydration,"Открыть счётчик",()->openTab(1));
    }
    void food(){
        LinearLayout actions=row(page);button(actions,"Записать блюдо",()->meal("",null,day));button(actions,"Снять фото",this::camera);
        heading(page,"Быстрый выбор");
        HorizontalScrollView gallery=new HorizontalScrollView(this);gallery.setHorizontalScrollBarEnabled(false);page.addView(gallery);
        LinearLayout strip=new LinearLayout(this);gallery.addView(strip);
        for(int i=0;i<DISH.length;i++){
            final int index=i;LinearLayout tile=column();tile.setBackground(bg(WHITE,16));pad(tile,8);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(180),-2);lp.rightMargin=dp(10);strip.addView(tile,lp);
            photo(tile,PHOTOS[i]);button(tile,DISH[i],()->meal(DISH[index],PHOTOS[index],day));
        }
        heading(page,"Приёмы пищи");entries();
    }
    void water(){
        int ml=total("water"),goal=getPreferences(0).getInt("goal",2000);
        LinearLayout c=card(0xffe0eee9);line(c,"Вода за день",16);TextView t=line(c,ml+" / "+goal+" мл",26);t.setTypeface(Typeface.DEFAULT_BOLD);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(goal);bar.setProgress(Math.min(ml,goal));c.addView(bar);
        LinearLayout amounts=row(c);for(int volume:new int[]{150,250,500})button(amounts,"+"+volume,()->insert(day,"water","Вода",volume,null));
        if(tab==1){button(c,"Другой объём",()->number("Сколько воды, мл?",250,5000,v->insert(day,"water","Вода",v,null)));button(c,"Изменить цель",()->number("Дневная цель, мл",goal,10000,v->{getPreferences(0).edit().putInt("goal",v).apply();render();}));}
    }
    interface Value {void accept(int value);}
    EditText input(LinearLayout box,String hint,int type){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setInputType(type);box.addView(e);return e;}
    int read(EditText e,int min,int max){try{int v=Integer.parseInt(e.getText().toString());if(v>=min&&v<=max)return v;}catch(NumberFormatException ignored){}e.setError("Введите число от "+min+" до "+max);return -1;}
    void number(String title,int initial,int max,Value callback){LinearLayout c=column();pad(c,16);EditText e=input(c,"мл",2);e.setText(String.valueOf(initial));e.selectAll();AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(c).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).show();d.getButton(-1).setOnClickListener(v->{int n=read(e,1,max);if(n>0){d.dismiss();callback.accept(n);}});}
    void insert(LocalDate date,String kind,String title,int value,String picture){ContentValues cv=new ContentValues();cv.put("day",date.toString());cv.put("kind",kind);cv.put("title",title);cv.put("amount",value);cv.put("photo",picture);db.insertOrThrow("entry",null,cv);render();Toast.makeText(this,"Запись сохранена",Toast.LENGTH_SHORT).show();}
    void photo(LinearLayout c,String name){if(name==null)return;try(InputStream stream=name.startsWith("shot-")?openFileInput(name):getAssets().open(name)){Bitmap img=BitmapFactory.decodeStream(stream);if(img==null)return;ImageView iv=new ImageView(this);iv.setImageBitmap(img);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setBackground(bg(PAPER,10));iv.setClipToOutline(true);c.addView(iv,new LinearLayout.LayoutParams(-1,dp(142)));}catch(IOException ignored){}}
    void meal(String name,String picture,LocalDate date){
        LinearLayout c=column();pad(c,16);photo(c,picture);EditText title=input(c,"Название блюда",InputType.TYPE_CLASS_TEXT);title.setText(name);
        EditText kcal=input(c,"Ккал в порции",InputType.TYPE_CLASS_NUMBER);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Приём пищи").setView(c).setNegativeButton("Отмена",null).setPositiveButton("Записать",null).show();
        dlg.getButton(-1).setOnClickListener(v->{String label=title.getText().toString().trim();if(label.isEmpty()){title.setError("Укажите название");return;}int n=read(kcal,0,10000);if(n>=0){insert(date,"meal",label,n,picture);dlg.dismiss();}});
    }
    void entries(){String sql="SELECT id,title,amount,kind,photo FROM entry WHERE day=?"+(tab==1?" AND kind='water'":tab==3?" AND kind='meal'":"")+" ORDER BY id DESC";try(Cursor cur=db.rawQuery(sql,new String[]{day.toString()})){if(!cur.moveToFirst()){line(page,"За этот день записей пока нет",16);return;}do{
        long id=cur.getLong(0);String title=cur.getString(1);LinearLayout c=card(WHITE);photo(c,cur.getString(4));line(c,title,20);line(c,cur.getInt(2)+("water".equals(cur.getString(3))?" мл":" ккал"),16);button(c,"Удалить",()->new AlertDialog.Builder(this).setMessage("Удалить запись «"+title+"»?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{db.delete("entry","id=?",new String[]{Long.toString(id)});render();}).show());
    }while(cur.moveToNext());}}
    void camera(){pendingDay=day.toString();try{startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE),7);}catch(ActivityNotFoundException e){Toast.makeText(this,"Приложение камеры недоступно",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=7||result!=RESULT_OK||data==null||data.getExtras()==null)return;Object image=data.getExtras().get("data");if(!(image instanceof Bitmap))return;Bitmap b=(Bitmap)image;String file="shot-"+UUID.randomUUID()+".jpg";LocalDate date=pendingDay==null?day:LocalDate.parse(pendingDay);
        try(FileOutputStream f=openFileOutput(file,MODE_PRIVATE)){b.compress(Bitmap.CompressFormat.JPEG,90,f);}catch(IOException e){Toast.makeText(this,"Не удалось сохранить фото",Toast.LENGTH_LONG).show();return;}
        ImageLabeler model=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        model.process(InputImage.fromBitmap(b,0)).addOnCompleteListener(task->{model.close();if(isFinishing()||isDestroyed())return;String suggestion="";if(task.isSuccessful()){Map<String,String> foods=new HashMap<>();String[][] pairs={{"Pizza","Пицца"},{"Salad","Салат"},{"Bread","Хлеб"},{"Fruit","Фрукты"},{"Vegetable","Овощи"},{"Cake","Торт"},{"Pasta","Паста"}};for(String[] p:pairs)foods.put(p[0],p[1]);for(ImageLabel l:task.getResult()){if(foods.containsKey(l.getText())){suggestion=foods.get(l.getText());break;}}}meal(suggestion,file,date);});
    }
}
