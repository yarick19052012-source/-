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
    void button(LinearLayout box,String name,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(name);b.setTextColor(GREEN);b.setOnClickListener(v->action.run());if(box.getOrientation()==0)box.addView(b,new LinearLayout.LayoutParams(0,-2,1));else box.addView(b);}
    LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout(this);parent.addView(r);return r;}
    LinearLayout card(int color){LinearLayout c=column();pad(c,16);c.setBackground(bg(color,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(c,lp);return c;}
    int total(String kind){try(Cursor c=db.rawQuery("SELECT coalesce(sum(amount),0) FROM entry WHERE day=? AND kind=?",new String[]{day.toString(),kind})){c.moveToFirst();return c.getInt(0);}}
    void render(){
        LinearLayout root=column();root.setBackgroundColor(PAPER);ScrollView scroll=new ScrollView(this);page=column();pad(page,20);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        line(page,"ТОПКА   /   ЛИЧНЫЙ ДНЕВНИК",12);heading(page,tab==0?"Мой день":tab==1?"Вода":"История");
        button(page,day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",ru)),()->new DatePickerDialog(this,(v,y,m,d)->{day=LocalDate.of(y,m+1,d);render();},day.getYear(),day.getMonthValue()-1,day.getDayOfMonth()).show());
        if(tab!=2)water();
        if(tab==0){
            heading(page,"Питание");line(page,"За день   "+total("meal")+" ккал",18);
            LinearLayout actions=row(page);button(actions,"Добавить блюдо",()->meal("",null,day));button(actions,"Камера",this::camera);
            for(int i=0;i<DISH.length;i++){final int n=i;LinearLayout c=card(WHITE);photo(c,PHOTOS[i]);button(c,DISH[i],()->meal(DISH[n],PHOTOS[n],day));}
        }
        if(tab!=0)entries();
        LinearLayout nav=row(root);String[] labels={"Мой день","Вода","История"};
        for(int i=0;i<3;i++){final int n=i;button(nav,labels[i],()->{tab=n;render();});nav.getChildAt(i).setEnabled(i!=tab);}
        setContentView(root);
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
    void entries(){String sql="SELECT id,title,amount,kind,photo FROM entry WHERE day=?"+(tab==1?" AND kind='water'":"")+" ORDER BY id DESC";try(Cursor cur=db.rawQuery(sql,new String[]{day.toString()})){if(!cur.moveToFirst()){line(page,"За этот день записей пока нет",16);return;}do{
        long id=cur.getLong(0);String title=cur.getString(1);LinearLayout c=card(WHITE);photo(c,cur.getString(4));line(c,title,20);line(c,cur.getInt(2)+("water".equals(cur.getString(3))?" мл":" ккал"),16);button(c,"Удалить",()->new AlertDialog.Builder(this).setMessage("Удалить запись «"+title+"»?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{db.delete("entry","id=?",new String[]{Long.toString(id)});render();}).show());
    }while(cur.moveToNext());}}
    void camera(){pendingDay=day.toString();try{startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE),7);}catch(ActivityNotFoundException e){Toast.makeText(this,"Приложение камеры недоступно",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=7||result!=RESULT_OK||data==null||data.getExtras()==null)return;Object image=data.getExtras().get("data");if(!(image instanceof Bitmap))return;Bitmap b=(Bitmap)image;String file="shot-"+UUID.randomUUID()+".jpg";LocalDate date=pendingDay==null?day:LocalDate.parse(pendingDay);
        try(FileOutputStream f=openFileOutput(file,MODE_PRIVATE)){b.compress(Bitmap.CompressFormat.JPEG,90,f);}catch(IOException e){Toast.makeText(this,"Не удалось сохранить фото",Toast.LENGTH_LONG).show();return;}
        ImageLabeler model=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        model.process(InputImage.fromBitmap(b,0)).addOnCompleteListener(task->{model.close();if(isFinishing()||isDestroyed())return;String suggestion="";if(task.isSuccessful()){Map<String,String> foods=new HashMap<>();String[][] pairs={{"Pizza","Пицца"},{"Salad","Салат"},{"Bread","Хлеб"},{"Fruit","Фрукты"},{"Vegetable","Овощи"},{"Cake","Торт"},{"Pasta","Паста"}};for(String[] p:pairs)foods.put(p[0],p[1]);for(ImageLabel l:task.getResult()){if(foods.containsKey(l.getText())){suggestion=foods.get(l.getText());break;}}}meal(suggestion,file,date);});
    }
}
