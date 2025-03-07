package com.skkk.easytouch.Services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.skkk.easytouch.Configs;
import com.skkk.easytouch.MainActivity;
import com.skkk.easytouch.R;
import com.skkk.easytouch.Receiver.AdminManageReceiver;
import com.skkk.easytouch.Utils.NotificationUtils;
import com.skkk.easytouch.Utils.ShotScreenUtils;
import com.skkk.easytouch.Utils.SpUtils;
import com.skkk.easytouch.View.AppSelect.AppSelectActivity;
import com.skkk.easytouch.View.ClipsCollectionView;
import com.skkk.easytouch.View.SoftInputListenerView;

import java.util.List;

import static com.skkk.easytouch.Configs.TOUCH_UI_DIRECTION_LEFT;
import static com.skkk.easytouch.Utils.SpUtils.getInt;

/**
 * 创建于 2017/12/10
 * 作者 admin
 */
/*
* 
* 描    述：悬浮窗父类
* 作    者：ksheng
* 时    间：2017/12/10$ 15:08$.
*/
public class EasyTouchBaseService extends Service {

    private static final String TAG = "EasyTouchBaseService";

    /**
     * 窗口管理器
     */
    protected WindowManager windowManager;
    /**
     * 音量管理器
     */
    protected AudioManager audioManager;
    /**
     * 组件名称
     */
    protected ComponentName mAdminName;
    /**
     * 设备安全管理器
     */
    protected DevicePolicyManager mDPM;
    /**
     * 震动管理器
     */
    protected Vibrator vibrator;

    /**
     * 震动等级
     */
    protected int vibrateLevel = Configs.DEFAULT_VIBRATE_LEVEL;
    /**
     * 左右位置
     */
    protected int direction = Configs.TOUCH_UI_DIRECTION_LEFT;


    protected int menuDetailWidthMax = 320;
    protected int menuDetailWidthMin = 220;

    protected int menuDetailHeightMax = 160;
    protected int menuDetailHeightMin = 80;

    protected float wholeMenuWidth = 340f;
    private WindowManager.LayoutParams softInputLp;
    protected SoftInputListenerView softInputListenerView;

    private WindowManager.LayoutParams tomatoLp;
    protected RelativeLayout tomatoView;
    protected RelativeLayout rlTomatoSetup;
    protected RelativeLayout rlTomatoStart;
    private TextView tvTomatoTime;
    private TextView tvTomatoToDo;
    private EditText etClose;
    private EditText etSetTitle;
    private Button btnStart;
    private Button btnClose;

    /**
     * 软件盘弹出前的高度位置
     */
    protected int lastParamsY;
    protected boolean hasConfigurationChanged = false;

    protected int flagSoftInputChangeHeight = 300;

    protected int screenWidth;
    protected int screenHeight;

    protected int leftBorder;
    protected int rightBorder;
    private ImageReader mImageReader;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private static Intent mResultData = null;
    private long shakeTime;
    private long showTime;
    private SensorManager sensorManager;
    /**
     * 处理重力感应监听
     */
    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float[] values = event.values;
            //X轴方向的重力加速度，向右为正
            float x = values[0];
            //Y轴方向的重力加速度，向前为正
            float y = values[1];
            //Z轴方向的重力加速度，向上为正
            float z = values[2];

            int medumValue = 12;
            //判断是否抬手
            if (Math.abs(x) > medumValue || Math.abs(y) > medumValue
                    || Math.abs(z) > medumValue) {
                shakeTime = System.currentTimeMillis();
            }

            if (z < 9 && z > 2 && -2 < x && x < 2 && 4 < y && y < 10) {


                showTime = System.currentTimeMillis();
                if (showTime - shakeTime > 0 && showTime - shakeTime < 500) {

                    shakeTime = 0;
                    wakeLock.acquire();
                    wakeLock.release();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };
    //亮屏
    private PowerManager.WakeLock wakeLock;
    //剪贴板布局
    private WindowManager.LayoutParams mClipsParam;
    private ClipsCollectionView clipsCollectionView;
    //是否需要横屏隐藏
    protected boolean needLandscapeHide;
    private CountDownTimer countDownTimer;


    @Override
    public void onCreate() {
        super.onCreate();
        //设置界面窗口管理器
        if (windowManager == null) {
            windowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        }
        //设置音量管理器
        audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        //设置设置管理器
        mAdminName = new ComponentName(this, AdminManageReceiver.class);
        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        //设置震动管理器
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        Point size = new Point();
        windowManager.getDefaultDisplay().getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        leftBorder = 0;
        rightBorder = screenWidth;

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            flagSoftInputChangeHeight = Math.max(screenWidth, screenHeight) / 3;
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            flagSoftInputChangeHeight = Math.min(screenWidth, screenHeight) / 3;
        }

        initUI();
    }


    private void initUI() {
        //设置输入法监听的悬浮view
        initSoftInput();

        //设置剪贴板监听
//        initClipBoard();

        //初始化番茄学习功能
        initTomato();

        //初始化重力感应
        initSensorEvent();
    }

    /**
     * 设置输入法监听的悬浮view
     */
    private void initSoftInput() {
        softInputLp = new WindowManager.LayoutParams();
        softInputLp.width = 0;
        softInputLp.x = 0;
        softInputLp.height = WindowManager.LayoutParams.MATCH_PARENT;
        softInputLp.type = WindowManager.LayoutParams.TYPE_PHONE;
        softInputLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        softInputLp.format = PixelFormat.TRANSPARENT;
        softInputLp.gravity = Gravity.LEFT | Gravity.TOP;
        softInputListenerView = new SoftInputListenerView(this);
        softInputListenerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        windowManager.addView(softInputListenerView, softInputLp);
    }

    /**
     * 初始化番茄学习功能
     */
    private void initTomato() {
        tomatoLp = new WindowManager.LayoutParams();
        tomatoLp.width = WindowManager.LayoutParams.MATCH_PARENT;
        tomatoLp.height = WindowManager.LayoutParams.MATCH_PARENT;
        tomatoLp.x = 0;
        tomatoLp.y = 0;
        tomatoLp.type = WindowManager.LayoutParams.TYPE_PHONE;
        tomatoLp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        tomatoLp.format = PixelFormat.TRANSPARENT;
        tomatoLp.gravity = Gravity.LEFT | Gravity.TOP;
        tomatoView = (RelativeLayout) View.inflate(getApplicationContext(), R.layout.layout_tomato_view, null);
        rlTomatoSetup = (RelativeLayout) tomatoView.findViewById(R.id.rl_tomato_setup);
        rlTomatoStart = (RelativeLayout) tomatoView.findViewById(R.id.rl_tomato_start);
        tvTomatoTime = (TextView) tomatoView.findViewById(R.id.tv_time);
        tvTomatoToDo = (TextView) tomatoView.findViewById(R.id.tv_to_do);
        etClose = (EditText) tomatoView.findViewById(R.id.et_close);
        etSetTitle = (EditText) tomatoView.findViewById(R.id.et_set_title);
        btnStart = (Button) tomatoView.findViewById(R.id.btn_start);
        btnClose = (Button) tomatoView.findViewById(R.id.btn_close);

        //设置开始按钮
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (etSetTitle.length() <= 0) {
                    etSetTitle.setError("请输入待办事项");
                    return;
                }
                SpUtils.saveString(getApplicationContext(), SpUtils.KEY_TOMATO_TITLE, etSetTitle.getText().toString().trim());
                startTomato();
            }
        });

        //关闭番茄
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeTomato();
            }
        });

        //设置关闭番茄学习的编辑框
        etClose.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String title = tvTomatoToDo.getText().toString().trim();
                char[] chars = title.toCharArray();
                StringBuffer buf = new StringBuffer();
                for (int i = chars.length - 1; i >= 0; i--) {
                    buf.append(chars[i]);
                }
                if (buf.toString().equals(s.toString())) {
                    closeTomato();
                }
            }
        });
    }

    /**
     * 初始化重力感应
     */
    private void initSensorEvent() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (SpUtils.getBoolean(getApplicationContext(), SpUtils.KEY_IDEA_FUNC_GRAVITY_SENSOR, false)) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.SCREEN_DIM_WAKE_LOCK, "WakeLock");
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        } else {
            sensorManager.unregisterListener(sensorEventListener);
        }
    }

    /**
     * 设置剪贴板监听
     */
    private void initClipBoard() {
        //设置悬浮窗的LP
        mClipsParam = new WindowManager.LayoutParams();
        mClipsParam.packageName = getPackageName();
        mClipsParam.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mClipsParam.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mClipsParam.flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mClipsParam.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        mClipsParam.format = PixelFormat.RGBA_8888;
        mClipsParam.gravity = Gravity.LEFT | Gravity.TOP;

        clipsCollectionView = new ClipsCollectionView(this);
        ViewGroup.LayoutParams clipViewLp = new ViewGroup.LayoutParams(screenWidth * 2 / 3, screenHeight / 4);
        clipsCollectionView.setLayoutParams(clipViewLp);

        mClipsParam.x = screenWidth / 6;
        mClipsParam.y = 100;

        windowManager.addView(clipsCollectionView, mClipsParam);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        direction = getInt(getApplicationContext(), Configs.KEY_TOUCH_UI_DIRECTION, TOUCH_UI_DIRECTION_LEFT);
        NotificationUtils.removeNotification(this);
        needLandscapeHide = SpUtils.getBoolean(getApplicationContext(), Configs.KEY_TOUCH_LANDSCAPE_HIDE, false);
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 按下返回键
     */
    protected void enterBack() {
        vibrator.vibrate(vibrateLevel);
        monitorSystemAction(FloatService.getService(), AccessibilityService.GLOBAL_ACTION_BACK);

    }

    /**
     * 按下HOME键
     */
    protected void enterHome() {
        vibrator.vibrate(vibrateLevel);
        monitorSystemAction(FloatService.getService(), AccessibilityService.GLOBAL_ACTION_HOME);

    }

    /**
     * 按下RECENTS键
     */
    protected void enterRecents() {
        vibrator.vibrate(vibrateLevel);
        monitorSystemAction(FloatService.getService(), AccessibilityService.GLOBAL_ACTION_RECENTS);

    }

    /**
     * 按下RECENTS键
     */
    protected void enterNotification() {
        vibrator.vibrate(vibrateLevel);
        monitorSystemAction(FloatService.getService(), AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
    }

    /**
     * 按下RECENTS键
     */
    protected void lockScreen() {
        if (mDPM.isAdminActive(mAdminName)) {
            //震动30毫秒
            vibrator.vibrate(vibrateLevel);
            mDPM.lockNow();
        }
    }

    /**
     * 跳转到上一个应用
     */
    protected void jump2LastApp() {
        monitorSystemAction(FloatService.getService(), AccessibilityService.GLOBAL_ACTION_RECENTS);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        vibrator.vibrate(vibrateLevel);
        monitorSystemAction(FloatService.getService(), AccessibilityService.GLOBAL_ACTION_RECENTS);
    }

    /**
     * 模拟全局按键
     *
     * @param service
     */
    public static void monitorSystemAction(AccessibilityService service, int action) {
        if (Build.VERSION.SDK_INT < 16) {
            Toast.makeText(service, "Android 4.1及以上系统才支持此功能，请升级后重试", Toast.LENGTH_SHORT).show();
        } else {
            service.performGlobalAction(action);
        }
    }

    /**
     * 打开App选择界面
     *
     * @param finalIndex
     * @param value
     */
    protected void startSelectAppActivity(int finalIndex, int value, Configs.TouchType touchType) {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(getApplicationContext(), AppSelectActivity.class);
        intent.putExtra(Configs.KEY_APP_TYPE, value);
        intent.putExtra(Configs.KEY_BALL_MENU_SELECT_APP_INDEX, finalIndex);
        intent.putExtra(Configs.KEY_TOUCH_TYPE, touchType.getValue());
        startActivity(intent);
        stopSelf();
    }

    /**
     * 工具 dip 2 px
     *
     * @param dp
     * @return
     */
    protected int dp2px(float dp) {
        final float scale = getApplicationContext().getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.1f);
    }


    /**
     * 跳转到APP
     */
    protected void showApp() {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(this, MainActivity.class);
        startActivity(intent);
    }


    /**
     * 截屏
     */
    protected void shotScreen() {
        if (ShotScreenUtils.checkServiceIsRun()) {
            Toast.makeText(this, "开始截屏", Toast.LENGTH_SHORT).show();
            ShotScreenUtils.getInstance().startScreenShot();
        } else {
            Toast.makeText(this, "请确认截屏权限是否开启", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 隐藏悬浮窗并且显示通知
     */
    protected void hideEasyTouchAndShowNotication() {
        NotificationUtils.sendNotification(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * 判断是否存在置顶的无障碍服务
     *
     * @param name
     * @return
     */
    public boolean isAccessibilityServiceRunning(String name) {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enableServices
                = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo enableService : enableServices) {
            if (enableService.getId().endsWith(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 设置番茄工作
     */
    public void setupTomato() {
        try {
            windowManager.addView(tomatoView, tomatoLp);
            rlTomatoSetup.setVisibility(View.VISIBLE);
            rlTomatoStart.setVisibility(View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "添加番茄界面失败：已经存在");
        }
    }

    /**
     * 开始番茄计时
     */
    public void startTomato() {
        rlTomatoSetup.setVisibility(View.GONE);
        rlTomatoStart.setVisibility(View.VISIBLE);

        tvTomatoToDo.setText(SpUtils.getString(getApplicationContext(), SpUtils.KEY_TOMATO_TITLE, "番茄学习法"));
        tvTomatoTime.requestFocus();
        countDownTimer = new CountDownTimer(25 * 60 * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                Log.i(TAG, "onTick: " + millisUntilFinished);
                tvTomatoTime.setText(String.format("%02d:%02d", millisUntilFinished / (1000 * 60), millisUntilFinished % (1000 * 60) / 1000));
            }

            @Override
            public void onFinish() {
                closeTomato();
            }
        };
        countDownTimer.start();


    }

    public void closeTomato() {
        try {
            windowManager.removeView(tomatoView);
            tvTomatoToDo.setText("完成");
            countDownTimer.cancel();
        } catch (Exception e) {
            Log.e(TAG, "删除番茄界面失败：不存在");
        }
    }

}
