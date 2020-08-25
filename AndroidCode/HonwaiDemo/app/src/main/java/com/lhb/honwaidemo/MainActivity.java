package com.lhb.honwaidemo;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.ConsumerIrManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView tvRec;
    private Socket socket;
    private String recStr;
    private InputStream in;
    private  OutputStream out;
    private  boolean isAirOpen=false;
    private int count=0;
    private int countMode=0;
    private int[] sendCMD=new int[CodeCommand.defaultOpenCMD.length];
    private static String[]  TEMPFLAG={"0000","1000","0100","1100","0010","1010","0110","1110","0001","1001","0101","1101","0011","1011","0111"};
    private static int[] TEMPFLAGINT={16,17,18,19,20,21,22,23,24,25,26,27,28,29,30};
    private static String[] MODESTRING={"000","100","010","110","001"};
    private static int[] MODEINT={1,2,3,4,5};
    private static String[] WINDSPEED={"00","10","01","11"};
    private static int[] WINDSPEEDINT={R.drawable.windspeed_status,R.drawable.first_status,R.drawable.second_status,R.drawable.third_status};
    private int nowTemp=16;
    private int tempInt=0;
    private Handler handler=new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what==0) {
                String handlerRecv= (String) msg.obj;
                tempInt=Integer.parseInt(handlerRecv.substring(handlerRecv.indexOf("：")+1,handlerRecv.indexOf("H")-2));
                String temp = handlerRecv.substring(0,handlerRecv.indexOf("H"))+"℃ ";
                String humi=handlerRecv.substring(handlerRecv.indexOf("H"))+"%rH";
                SimpleDateFormat f=new SimpleDateFormat("MM-dd-HH:mm:ss");
                tvRec.append(f.format(new Date())+"："+ temp +humi+"\n");
            } else if(msg.what==1) {
                tvRec.append(msg.obj+"");
            }else if(msg.what==2){
                tvAirTemp.setText(msg.obj+"");
                findViewById(R.id.btnWindSpeed).setBackgroundResource(msg.arg1);
            }
        }
    };
    private ConsumerIrManager consumerIrManager;
    private TextView tvAirTemp;
    private int windSpeedNum=4;
    private boolean isSmartOpen=false;
    private int MANUAL=0,AUTO=1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SharedPreferences sharedPreferences=getSharedPreferences("Setting",MODE_PRIVATE);
        final String spIP = sharedPreferences.getString("IP","192.168.1.108");
         final int spPort = sharedPreferences.getInt("PORT",8080);
        final int spTempFazhi = sharedPreferences.getInt("TEMPFAZHI",30);
       boolean flag=sharedPreferences.getBoolean("isSmartOpen",false);
        tvRec=findViewById(R.id.tvRecieve);
        tvAirTemp = findViewById(R.id.tvAirTemp);
        tvAirTemp.setText("空调未开启");
        for(int i=0;i<CodeCommand.defaultOpenCMD.length;i++){
            sendCMD[i]=CodeCommand.defaultOpenCMD[i];
        }
        consumerIrManager = (ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        //检测设备是否具有红外功能
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            if (!consumerIrManager.hasIrEmitter()) {
                Toast.makeText(this,"没有红外功能",Toast.LENGTH_SHORT).show();

            } else {
                Toast.makeText(this,"红外已就绪",Toast.LENGTH_SHORT).show();
            }
        }
        //通过socket通信与esp8266开发板连接，接收发送过来的温湿度数据
        final Thread thread=new Thread(new Runnable() {
            @Override
            public void run() {

                while(true){
                    try {
                        socket=new Socket(spIP, spPort);
                        if(socket.isConnected()){
                            in= socket.getInputStream();
                            out=socket.getOutputStream();
                            count++;
                            if(count<=1){
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        tvRec.append("已连接到DHT11温湿度传感器！\n");
                                    }
                                });
                            }
                        }
                        byte[] buf=new byte[1024];
                        int len=-1;
                        while ((len=in.read(buf))!=-1){
                            recStr=new String(buf,0,len);
                            Message msg=handler.obtainMessage(0,recStr);
                            handler.sendMessage(msg);
                            Thread.sleep(1000);
                        }

                    } catch (Exception e) {
                        //socket通信出现异常时提示检测网络状态
                        if(count==0){
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvRec.append("未连接到DHT11温湿度传感器！请检查网络连接后重试！\n");
                                }
                            });

                        }
                        e.printStackTrace();
                    }
                }

            }
        });
        thread.start();
        //智能控温线程
        Thread smartThread=new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){

                    if (isAirOpen) {
                        //打开睡眠模式
                        sendCMD[17]=1600;
                        if(tempInt>spTempFazhi){
                            //设定温度阈值小于当前室内温度值的时候使其发送温度调低指令给空调
                            controlTempKeyPress(16,AUTO);
                        }else if(tempInt<spTempFazhi){
                            //设定温度阈值大于当前室内温度值的时候使其发送温度调高指令给空调
                            controlTempKeyPress(30,AUTO);
                        }
                        try {
                            //该线程每隔1分钟检测一次室内温度是否大/小于阈值，时间可自行修改设定
                            Thread.sleep(60000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        //当智能控温模式设定关闭时，检测线程状态，如果为‘RUNNNING’状态，则中断此线程
        if(flag==false){
            if(smartThread.getState().toString()=="RUNNING")
                smartThread.interrupt();

        }else{
                smartThread.start();


        }
        //设定模式按键的长按点击事件，打开设置dialog
        findViewById(R.id.btnMode).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                final AlertDialog.Builder alertDialog= new AlertDialog.Builder(MainActivity.this);
                View view1=View.inflate(MainActivity.this,R.layout.dialog,null);
                final EditText etIP=view1.findViewById(R.id.etIP);
                final EditText etPort=view1.findViewById(R.id.etPort);
                final EditText etTempFazhi=view1.findViewById(R.id.etTempFazhi);
                final Switch swbtn=view1.findViewById(R.id.swbtn);
                SharedPreferences sharedPreferences=getSharedPreferences("Setting",MODE_PRIVATE);
                final String spIP = sharedPreferences.getString("IP","192.168.1.108");
                final int spPort = sharedPreferences.getInt("PORT",8080);
                int spTempFazhi = sharedPreferences.getInt("TEMPFAZHI",30);
                boolean flag=sharedPreferences.getBoolean("isSmartOpen",false);
                etIP.setText(spIP);
                etPort.setText(spPort+"");
                etTempFazhi.setText(spTempFazhi+"");
                if(flag){
                    swbtn.setText("智能控温模式：开");
                    swbtn.setChecked(true);
                }else{
                    swbtn.setText("智能控温模式：关");
                    swbtn.setChecked(false);
                }
                swbtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(swbtn.isChecked()){
                            swbtn.setText("智能控温模式：开");
                        }else{
                            swbtn.setText("智能控温模式：关");
                        }
                    }
                });
                //保存设置时，存下当前设置ip，port，阈值，智能控温模式开关状态，重启app后应用
                alertDialog.setTitle("设置").setView(view1).setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        SharedPreferences sharedPreferences=getSharedPreferences("Setting",MODE_PRIVATE);
                        SharedPreferences.Editor editor=sharedPreferences.edit();
                        if(!etIP.getText().toString().trim().isEmpty() && !etPort.getText().toString().trim().isEmpty() && !etTempFazhi.getText().toString().trim().isEmpty()){
                            editor.putString("IP",etIP.getText().toString().trim());
                            editor.putInt("PORT",Integer.parseInt(etPort.getText().toString().trim()));
                            editor.putInt("TEMPFAZHI",Integer.parseInt(etTempFazhi.getText().toString().trim()));
                            if(swbtn.getText().toString().equals("智能控温模式：关")){
                                isSmartOpen=false;
                            }else if(swbtn.getText().toString().equals("智能控温模式：开")){
                                isSmartOpen=true;
                            }
                            editor.putBoolean("isSmartOpen",isSmartOpen);
                            editor.commit();
                        }else{
                            Toast.makeText(MainActivity.this,"设置项不能为空，请重试！",Toast.LENGTH_SHORT).show();
                        }


                    }
                }).setNegativeButton("取消",null);

              AlertDialog dialog= alertDialog.create();
               dialog.show();
               dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.rgb(229, 28, 35));
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.rgb(229, 28, 35));

                return false;
            }
        });

            Log.d("thread state",smartThread.getState()+"");

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            socket.close();
            in.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Process.killProcess(Process.myPid());
    }

    public void btnClick(View view){

        switch (view.getId()){
            case R.id.btnDefaultCMD:
                if(!isAirOpen){
                    openMethod(CodeCommand.defaultOpenCMD,windSpeedNum,MANUAL);

                }else if(isAirOpen) {
                    consumerIrManager.transmit(38000,CodeCommand.defaultCloseCMD);
                    isAirOpen=false;
                    tvAirTemp.setText("空调未开启");
                    tvRec.append("空调已关闭！\r\n");
                }
                break;
            case R.id.btnPlus:

                controlTempKeyPress(30,MANUAL);

                break;
            case R.id.btnLess:
                controlTempKeyPress(16,MANUAL);
                break;
            case R.id.btnWindSpeed:

                if(!isAirOpen){
                    for(int i=0;i<WINDSPEED.length;i++){
                        if(WINDSPEED[i]==getAirSpeed(sendCMD).getWindSpeedStr()){
                            windSpeedNum=getAirSpeed(sendCMD).getWindSpeedCode();
                            break;
                        }
                    }
                }else{
                    windSpeedNum++;
                    if(windSpeedNum>4){
                        windSpeedNum=1;

                    }else{
                        char[] chr=WINDSPEED[windSpeedNum-1].toCharArray();
                        if(chr[0]=='0'){
                            sendCMD[11]=600;
                        } else if (chr[0] == '1') {
                            sendCMD[11]=1600;
                        }
                        if(chr[1]=='0'){
                            sendCMD[13]=600;
                        }else if(chr[1]=='1'){
                            sendCMD[13]=1600;
                        }
                    }

                }
                openMethod(sendCMD,windSpeedNum,MANUAL);



                break;
            case R.id.btnMode:
                char[] chr={};
                   for (int i=0;i<MODESTRING.length;i++){
                       if(MODESTRING[i]==getAirMode(sendCMD).getModeNum()){
                            countMode=MODEINT[i];
                            break;
                       }
                   }
                   countMode++;
                if(countMode>5){
                    countMode=1;
                }
                chr=MODESTRING[countMode-1].toCharArray();
                sendCMD[19]=1600;
                sendCMD[21]=600;
                sendCMD[23]=600;
                sendCMD[25]=1600;
                nowTemp=25;
                if (chr[0]=='0'){
                    sendCMD[3]=600;
                }else if(chr[0]=='1'){
                    sendCMD[3]=1600;
                }
                if (chr[1]=='0'){
                    sendCMD[5]=600;
                }else if(chr[1]=='1'){
                    sendCMD[5]=1600;
                }
                if (chr[2]=='0'){
                    sendCMD[7]=600;
                }else if(chr[2]=='1'){
                    sendCMD[7]=1600;
                }
                changeVerification(sendCMD);

                openMethod(sendCMD,windSpeedNum,MANUAL);

                break;

        }

    }
//控制空调温度升高降低的方法，具体修改方法参考格力空调红外协议编码表
    private void controlTempKeyPress(int param,int type) {
        String nowSettingTempStr ="";
        if(!isAirOpen){

                String str=getAirTemp(sendCMD).substring(0,getAirTemp(sendCMD).indexOf("℃"));
                nowTemp=Integer.parseInt(str);
            if (param==30) {
                if(nowTemp>30){
                    nowTemp=30;
                    if(type==1){
                        Message msg=handler.obtainMessage(1,"已达到设备可设定温度最大值！\n");
                        handler.sendMessage(msg);
                    }else{
                        tvRec.append("已达到设备可设定温度最大值！\r\n");
                    }

                    return;
                }else{
                    nowSettingTempStr =new StringBuffer(getByteBinary(Byte.valueOf(str))).reverse().toString();
                    Log.d("当前温度",str);
                }
            } else if(param==16) {
                if(nowTemp<16){
                    nowTemp=16;
                    if(type==1){
                        Message msg=handler.obtainMessage(1,"已达到设备可设定温度最小值！\n");
                        handler.sendMessage(msg);
                    }else{
                        tvRec.append("已达到设备可设定温度最小值！\r\n");
                    }

                    return;
                }else{
                    nowSettingTempStr =new StringBuffer(getByteBinary(Byte.valueOf(str))).reverse().toString();
                    Log.d("当前温度",str);
                }
            }
        }else if(isAirOpen){
            if (param==30) {
                String str= null;
                try {
                    str = tvAirTemp.getText().toString().substring(tvAirTemp.getText().toString().indexOf(" ")+1,tvAirTemp.getText().toString().indexOf("℃"));
                    nowTemp=Integer.parseInt(str);
                    nowTemp++;

                } catch (Exception e) {
                    nowTemp=25;
                    e.printStackTrace();
                }
                if(nowTemp>30){
                    nowTemp=30;

                    if(type==1){
                        Message msg=handler.obtainMessage(1,"已达到设备可设定温度最大值！\n");
                        handler.sendMessage(msg);
                    }else{
                        tvRec.append("已达到设备可设定温度最大值！\r\n");
                    }
                    return;
                }else{
                    str=String.valueOf(nowTemp);
                    nowSettingTempStr=getByteBinary(Byte.valueOf(str));
                    Log.d("当前温度",nowTemp+"");
                }
            } else if(param==16) {
                String str= null;
                try {
                    str = tvAirTemp.getText().toString().substring(tvAirTemp.getText().toString().indexOf(" ")+1,tvAirTemp.getText().toString().indexOf("℃"));

                    nowTemp=Integer.parseInt(str);
                    nowTemp--;
                } catch (Exception e) {
                    nowTemp=25;
                    e.printStackTrace();
                }
                if(nowTemp<16){
                    nowTemp=16;
                    if(type==1){
                        Message msg=handler.obtainMessage(1,"已达到设备可设定温度最小值！\n");
                        handler.sendMessage(msg);
                    }else{
                        tvRec.append("已达到设备可设定温度最小值！\r\n");
                    }
                    return;
                }else{
                    str=String.valueOf(nowTemp);
                    nowSettingTempStr=getByteBinary(Byte.valueOf(str));
                    Log.d("当前温度",nowTemp+"");
                }
            }

        }

        for(int i=0;i<TEMPFLAG.length;i++){
            if(TEMPFLAG[i].equals(nowSettingTempStr)){

                nowSettingTempStr=TEMPFLAG[i];
                break;
            }
        }

        char[] chr= nowSettingTempStr.toCharArray();
        if(chr[0]=='1'){
            sendCMD[19]=1600;
        }else if(chr[0]=='0'){
            sendCMD[19]=600;
        }
        if(chr[1]=='1'){
            sendCMD[21]=1600;
        }else if(chr[1]=='0'){
            sendCMD[21]=600;
        }
        if(chr[2]=='1'){
            sendCMD[23]=1600;
        }else if(chr[2]=='0'){
            sendCMD[23]=600;
        }
        if(chr[3]=='1'){
            sendCMD[25]=1600;
        }else if(chr[3]=='0'){
            sendCMD[25]=600;
        }
        Log.d("nowSettingTempStr",nowSettingTempStr);
        changeVerification(sendCMD);
        if(type==1){
            openMethod(sendCMD,windSpeedNum,AUTO);
        }else {
            openMethod(sendCMD,windSpeedNum,MANUAL);
        }



    }
//开启空调的指令发送方法
    private void openMethod(int[] cmd,int num,int type) {
        consumerIrManager.transmit(38000, cmd);
        isAirOpen=true;
        if(type==1){
            Message msg=handler.obtainMessage(2,WINDSPEEDINT[num-1],0,getAirMode(cmd).getModeStr()+" "+getAirTemp(cmd));
            handler.sendMessage(msg);
        }else{
            tvAirTemp.setText(getAirMode(cmd).getModeStr()+" "+getAirTemp(cmd));
            findViewById(R.id.btnWindSpeed).setBackgroundResource(WINDSPEEDINT[num-1]);
        }



    }
//计算并设置每次发送指令时的校验码，校验码如何计算参考格力空调红外协议编码表
    public void changeVerification(int[] cmd) {
        int modeNum=getAirMode(cmd).getModeCode();

        int veriByte= Byte.valueOf(String.valueOf((modeNum-1)+Integer.parseInt(getAirTemp(sendCMD).substring(0,getAirTemp(sendCMD).indexOf("℃")))-16
               +5+Integer.parseInt(manageVoltageCMD(sendCMD[82],sendCMD[83]))
               +Integer.parseInt(manageVoltageCMD(sendCMD[50],sendCMD[51]))
               +Integer.parseInt(manageVoltageCMD(sendCMD[126],sendCMD[127]))
               -Integer.parseInt(manageVoltageCMD(sendCMD[8],sendCMD[9]))));
        String str= toBinaryString(veriByte);

       str=new StringBuffer(str).reverse().toString();
        Log.d("校验码",str);
        char[] chr=str.toCharArray();
        if(chr[0]=='1'){
            sendCMD[131]=1600;
        }else if(chr[0]=='0'){
            sendCMD[131]=600;
        }
        if(chr[1]=='1'){
            sendCMD[133]=1600;
        }else if(chr[1]=='0'){
            sendCMD[133]=600;
        }
        if(chr[2]=='1'){
            sendCMD[135]=1600;
        }else if(chr[2]=='0'){
            sendCMD[135]=600;
        }
        if(chr[3]=='1'){
            sendCMD[137]=1600;
        }else if(chr[3]=='0'){
            sendCMD[137]=600;
        }


    }

//获取当前发送指令的空调模式
    public AirMode getAirMode(int[] cmd){
        String modeStr="";
        String modeNum="";
        int modeCode=0;
        if(cmd[2]==600  && cmd[3]==600){
            if(cmd[4]==600  && cmd[5]==600){
                if(cmd[6]==600  && cmd[7]==600){
                    modeStr="自动模式";
                    modeNum="000";
                    modeCode=1;
                }else if(cmd[8]==600  && cmd[9]==1600){
                    modeStr="制热模式";
                    modeNum="001";
                    modeCode=5;
                }

            }else if(cmd[4]==600  && cmd[5]==1600){
                modeStr="加湿模式";
                modeNum="010";
                modeCode=3;
            }

        }else if(cmd[2]==600  && cmd[3]==1600){
            if(cmd[4]==600  && cmd[5]==600){
                modeStr="制冷模式";
                modeNum="100";
                modeCode=2;

            }else if(cmd[4]==600  && cmd[5]==1600){
                modeStr="送风模式";
                modeNum="110";
                modeCode=4;
            }
        }
        return new AirMode(modeStr,modeNum,modeCode);
    }
    //获取当前发送指令的空调温度
    public String getAirTemp(int[] cmd){
        String tempStr="";
        String cmdStr=manageVoltageCMD(cmd[18], cmd[19])+ manageVoltageCMD(cmd[20],cmd[21])+
                manageVoltageCMD(cmd[22],cmd[23])+ manageVoltageCMD(cmd[24],cmd[25]);
        for (int i=0;i<TEMPFLAG.length;i++){
            if(TEMPFLAG[i].equals(cmdStr)) {
                tempStr=TEMPFLAGINT[i]+"℃";
            }
        }

       return tempStr;
    }
//获取当前发送指令的空调风速
    public WindSpeed getAirSpeed(int[] cmd){
        String windSpeedStr="";
        int windSpeedCode= 0;
        int imagePath=0;
        if(cmd[11]==1600){
            if(cmd[13]==1600){
                windSpeedStr="11";
                windSpeedCode=4;
                imagePath=R.drawable.third_status;
            }else if(cmd[13]==600){
                windSpeedStr="10";
                windSpeedCode=2;
                imagePath=R.drawable.first_status;
            }
        }else if(cmd[11]==600){
            if(cmd[13]==1600){
                windSpeedStr="01";
                windSpeedCode=3;
                imagePath=R.drawable.second_status;
            }else if(cmd[13]==600){
                windSpeedStr="00";
                windSpeedCode=1;
                imagePath=R.drawable.windspeed_status;
            }
        }
        return new WindSpeed(windSpeedStr,windSpeedCode,imagePath);
    }
    //将红外指令转换为0/1高低电平的方法
    public String manageVoltageCMD(int num1,int num2){
        int result=0;
        if(num1==600 && num2==600){
           result=0;
        }else if(num1==600 && num2==1600){
            result=1;
        }
       
        return String.valueOf(result);
    }
    //获取温度值十进制转换二进制后的后四位，用于设置温度指令
    public String getByteBinary(byte bNum){
        String str="";
        for (int i=0;i<TEMPFLAG.length;i++){
            if((byte) TEMPFLAGINT[i]==bNum){
                str=TEMPFLAG[i];
                break;
            }
        }
        return str;
    }
    //获取计算出的校验码的二进制后四位，用于校验码指令设定
    public  String toBinaryString(int num) {
        String manageStr=Integer.toBinaryString(num&0x0f);
        if(manageStr=="0")
            manageStr= "0000";


        for (int i=manageStr.length();i<4;i++){
            manageStr="0"+manageStr;
        }

        return manageStr;
    }

}
