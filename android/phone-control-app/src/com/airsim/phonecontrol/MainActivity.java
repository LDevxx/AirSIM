package com.airsim.phonecontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int ROLE_REQUEST = 100;
    private static final int DEFAULT_APPS_REQUEST = 102;
    private static final int OPTIONAL_SMS_REQUEST = 103;
    private static final int CALL_ACCOUNT_PERMISSION_REQUEST = 104;
    private static final int SHIZUKU_SETUP_REQUEST = 105;
    private static final int DIAGNOSTIC_TXT_REQUEST = 106;
    private static final java.util.concurrent.ExecutorService DIAGNOSTIC_WORKER = Executors.newSingleThreadExecutor();
    private static final String SHIZUKU_STARTUP_CHECKED = "shizuku_startup_checked";
    private static final String OPTIONAL_SMS_PROMPTED = "optional_sms_prompted";
    private static final String THEME_MODE_KEY = "theme_mode";
    private static final java.util.concurrent.ExecutorService SMS_SCAN_WORKER =
            Executors.newSingleThreadExecutor();

    // 主题色统一从 res/values/colors.xml 读取（原先硬编码在此处）；
    // onCreate 里最先调用 loadThemeColors()，一处修改全局生效。
    private int BG;
    private int SURFACE;
    private int SURFACE_HIGH;
    private int BORDER;
    private int PRIMARY;
    private int CYAN;
    private int BLUE;
    private int TEXT;
    private int MUTED;
    private int WARNING;
    private int DANGER;
    // Material 3 风格导航与应用栏的扩展色。
    private int navActiveContainer;
    private int navRipple;
    private int appBarSurface;
    // 强调色 / 警示色的半透明容器底色（图标圆底、徽章、分段选择器用），随明暗主题派生。
    private int tintContainer;
    private int warnContainer;

    private void loadThemeColors() {
        android.content.res.Resources res = getResources();
        boolean light = isLightTheme();
        BG = res.getColor(light ? R.color.airsim_bg_light : R.color.airsim_bg, getTheme());
        SURFACE = res.getColor(light ? R.color.airsim_surface_light : R.color.airsim_surface, getTheme());
        SURFACE_HIGH = res.getColor(light ? R.color.airsim_surface_high_light : R.color.airsim_surface_high, getTheme());
        BORDER = res.getColor(light ? R.color.airsim_border_light : R.color.airsim_border, getTheme());
        // PRIMARY 不再读固定资源，由用户选择的主题强调色决定（默认薄荷绿）。
        CYAN = res.getColor(light ? R.color.airsim_cyan_light : R.color.airsim_cyan, getTheme());
        BLUE = res.getColor(light ? R.color.airsim_blue_light : R.color.airsim_blue, getTheme());
        TEXT = res.getColor(light ? R.color.airsim_text_light : R.color.airsim_text, getTheme());
        MUTED = res.getColor(light ? R.color.airsim_muted_light : R.color.airsim_muted, getTheme());
        WARNING = res.getColor(light ? R.color.airsim_warning_light : R.color.airsim_warning, getTheme());
        DANGER = res.getColor(light ? R.color.airsim_danger_light : R.color.airsim_danger, getTheme());
        PRIMARY = accentColor("blue");
        // Material 3 选中胶囊：当前强调色的 12% 透明度容器，随主题实时派生。
        navActiveContainer = Color.argb(0x1E,
                Color.red(PRIMARY), Color.green(PRIMARY), Color.blue(PRIMARY));
        navRipple = res.getColor(light ? R.color.airsim_nav_ripple_light : R.color.airsim_nav_ripple, getTheme());
        appBarSurface = res.getColor(light ? R.color.airsim_app_bar_surface_light : R.color.airsim_app_bar_surface, getTheme());
        // 容器色：强调色/警示色的 16-18% 透明底，暗色更亮一档保证可读。
        tintContainer = Color.argb(light ? 0x2E : 0x30,
                Color.red(PRIMARY), Color.green(PRIMARY), Color.blue(PRIMARY));
        warnContainer = Color.argb(light ? 0x2E : 0x30,
                Color.red(WARNING), Color.green(WARNING), Color.blue(WARNING));
        agentRefreshColor = MUTED;
    }

    /** 顶部主题色选择：读取当前强调色资源（LDev：固定用海洋蓝，不再提供强调色选择）。 */
    private int accentColor(String key) {
        android.content.res.Resources res = getResources();
        boolean light = isLightTheme();
        return res.getColor(light ? R.color.airsim_theme_blue_light : R.color.airsim_theme_blue, getTheme());
    }

    /** 当前主题明暗模式（LDev）：dark=暗黑灰（默认），light=亮白蓝。 */
    private boolean isLightTheme() {
        return "light".equals(getPreferences(MODE_PRIVATE).getString(THEME_MODE_KEY, "dark"));
    }

    private enum Page { STATUS, CALLS, DEBUG, SETTINGS }
    private enum Feature { VOWLAN, AGENT, PCM, PUSH, SHIZUKU }

    private FrameLayout contentHost;
    private LinearLayout bottomBar;
    private TextView appBarTitle;
    private ImageButton appBarOverflow;
    private ImageButton appBarTheme;
    private final List<NavigationItemView> navigationItems = new ArrayList<>();
    private Page currentPage = Page.STATUS;

    /** Material 3 风格底部导航项：胶囊指示器 + 图标 + 标签，整项带涟漪反馈。 */
    private static final class NavigationItemView {
        final LinearLayout view;
        final View pill;
        final ImageView iconView;
        final TextView labelView;
        final Page page;

        NavigationItemView(LinearLayout view, View pill, ImageView iconView,
                           TextView labelView, Page page) {
            this.view = view;
            this.pill = pill;
            this.iconView = iconView;
            this.labelView = labelView;
            this.page = page;
        }
    }
    private HealthTile pcmHealthTile;
    private LiveStatusPoller pcmStatusPoller;
    private boolean resumed;

    private EditText endpoint;
    private EditText token;
    private EditText number;
    private String selectedMode;
    private TextView status;
    private TextView shizukuStatus;
	private TextView installerStatus;
    private TextView pairingStatus;
    private TextView pairingCodeView;
    private TextView pairingCountdownView;
    private TextView pairingDeviceInfoView;
    private TextView pairingPortsView;
    private TextView pairingActionButton;
    private TextView voBLEAuthenticationStatusView;
    private TextView voBLEPairingCodeView;
    private TextView voBLEDiagnosticsView;
    private TextView voBLEClearCredentialButton;
    private boolean voBLEAuthenticationVisible;
    private LinearLayout debugRows;
    private String lastRenderedDebugLog;
    private String lastRenderedDebugFilter;
    private TextView agentRefreshResult;
    private String agentRefreshMessage = "点击刷新以查看 Agent 的实际返回结果";
    // 主题在 loadThemeColors() 中恢复默认灰色提示色；不能在字段初始化时读取，
    // 因为那时主题色尚未从资源加载。
    private int agentRefreshColor;
    private int agentRefreshGeneration;
    // 设备信息区块：机主主动点击才生成，复制与反馈完全由机主决定。
    private TextView deviceInfoText;
    private TextView deviceInfoCopyButton;
    private String deviceInfoValue = "";
    private String activeDebugFilter = "全部";
    private boolean realtimeLogging = true;
    private boolean agentOnline;
    private String agentDetail = "待检测";
    private boolean pushConfigured;
    private String pushDetail = "尚未检查";
    private String pushDiagnostic = "尚未读取 Agent 的 Push 状态";
	private boolean cloudEnabled;
	private boolean cloudRelayConfigured;
	private String cloudLastError = "";
	private boolean avfStartupPromptShown;
	private boolean crashPromptShown;
    private ShizukuStartupPolicy shizukuStartupPolicy;
    private boolean startupSetupPending;
    private boolean startupSetupInFlight;
    private boolean startupSetupCompleted;
    private AlertDialog shizukuStartupDialog;
    private int selectedDialSlot = -1;
    private final Runnable startupSetup = () -> {
        if (startupSetupPending) continueStartupSetup();
    };

    private PairingServer pairingServer;
    private CountDownTimer pairingCountdown;
    private String pairingCode = "";
    private long pairingExpiresAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debugRefresh = new Runnable() {
        @Override public void run() {
            if (currentPage == Page.DEBUG && realtimeLogging) {
                refreshDebugLog();
                handler.postDelayed(this, 2_000);
            }
        }
    };
    private final Runnable voBLEAuthenticationRefresh = new Runnable() {
        @Override public void run() {
            if (!voBLEAuthenticationVisible) return;
            renderVoBLEAuthenticationStatus();
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        loadThemeColors();
        shizukuStartupPolicy = new ShizukuStartupPolicy(
                state != null && state.getBoolean(SHIZUKU_STARTUP_CHECKED, false));
        pcmStatusPoller = new LiveStatusPoller(() -> {
            ShizukuBridgeManager bridge = ShizukuBridgeManager.get(this);
            bridge.refreshBridgeStatus();
            return bridge.bridgeRunning();
        }, this::renderPCMStatus, new LiveStatusPoller.Scheduler() {
            @Override public void postDelayed(Runnable task, long delayMillis) {
                handler.postDelayed(task, delayMillis);
            }
            @Override public void removeCallbacks(Runnable task) {
                handler.removeCallbacks(task);
            }
        }, 1_000);
        configureWindow();
        setContentView(buildShell());
        showPage(Page.STATUS);
        Uri data = getIntent().getData();
        if (Intent.ACTION_DIAL.equals(getIntent().getAction()) && data != null) {
            showPage(Page.CALLS);
            if (number != null) number.setText(data.getSchemeSpecificPart());
        }
        refreshStatus();
        refreshAgent();
		String[] missingPermissions = RuntimePermissionPolicy.missingPermissions(
				Build.VERSION.SDK_INT,
				checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
				checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED,
				checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
				checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED,
				checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED,
				Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
						== PackageManager.PERMISSION_GRANTED,
				Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
						== PackageManager.PERMISSION_GRANTED,
				Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
						== PackageManager.PERMISSION_GRANTED);
		if (missingPermissions.length > 0) {
            requestPermissions(missingPermissions, 101);
		} else {
			requestOptionalSMSPermission();
		}
    }

	@Override public void onRequestPermissionsResult(
			int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == 101) requestOptionalSMSPermission();
		if (requestCode == 101 && hasVoBLEPermissions() && FeatureFlags.voBLEAvailable(this)) {
			com.airsim.phonecontrol.voble.VoBLEGatewayService.start(this);
		}
		if (requestCode == OPTIONAL_SMS_REQUEST) {
			startSMSInboxBaselineIfAuthorized();
			continueStartupSetup();
			if (currentPage == Page.SETTINGS) showPage(Page.SETTINGS);
		}
		if (requestCode == CALL_ACCOUNT_PERMISSION_REQUEST && currentPage == Page.SETTINGS) {
			showPage(Page.SETTINGS);
		}
		if ((requestCode == 101 || requestCode == 104)
				&& (currentPage == Page.STATUS || currentPage == Page.CALLS)) {
			showPage(currentPage);
		}
	}

	private void requestOptionalSMSPermission() {
		boolean granted = checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
		boolean prompted = getPreferences(MODE_PRIVATE).getBoolean(OPTIONAL_SMS_PROMPTED, false);
		String[] missing = RuntimePermissionPolicy.optionalInboxPermissions(granted, prompted);
		if (missing.length == 0) {
			continueStartupSetup();
			return;
		}
		getPreferences(MODE_PRIVATE).edit().putBoolean(OPTIONAL_SMS_PROMPTED, true).apply();
		requestPermissions(missing, OPTIONAL_SMS_REQUEST);
	}

	private boolean hasVoBLEPermissions() {
		return Build.VERSION.SDK_INT < 31
				|| (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
				&& checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED);
	}

	private void startSMSInboxBaselineIfAuthorized() {
		if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
			SMS_SCAN_WORKER.execute(() -> SMSInboxScanner.scanIfDue(getApplicationContext()));
		}
	}

    private void configureWindow() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(false);

        root.addView(buildAppBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottomBar = buildBottomBar();
        root.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(0, bars.top, 0, bars.bottom);
            } else {
                view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        return root;
    }

    private LinearLayout buildAppBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(12), dp(10), dp(8));
        bar.setBackgroundColor(appBarSurface);
        appBarTitle = label("", 20, TEXT, Gravity.START);
        appBarTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(appBarTitle, weighted());

        appBarTheme = new ImageButton(this);
        appBarTheme.setImageResource(R.drawable.ic_palette);
        appBarTheme.setImageTintList(ColorStateList.valueOf(TEXT));
        appBarTheme.setBackground(circularRipple());
        appBarTheme.setContentDescription("主题色");
        appBarTheme.setOnClickListener(ignored -> showThemeMenu(appBarTheme));
        bar.addView(appBarTheme, new LinearLayout.LayoutParams(dp(44), dp(44)));

        appBarOverflow = new ImageButton(this);
        appBarOverflow.setImageResource(R.drawable.ic_more);
        appBarOverflow.setImageTintList(ColorStateList.valueOf(TEXT));
        appBarOverflow.setBackground(circularRipple());
        appBarOverflow.setContentDescription("更多操作");
        appBarOverflow.setOnClickListener(ignored -> showOverflowMenu(appBarOverflow));
        bar.addView(appBarOverflow, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private LinearLayout buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.setBackgroundColor(SURFACE);
        navigationItems.clear();
        bar.addView(navigationItem("状态", R.drawable.nav_status, Page.STATUS), weighted());
        bar.addView(navigationItem("通话", R.drawable.nav_calls, Page.CALLS), weighted());
        bar.addView(navigationItem("调试", R.drawable.nav_debug, Page.DEBUG), weighted());
        bar.addView(navigationItem("设置", R.drawable.nav_settings, Page.SETTINGS), weighted());
        return bar;
    }

    private LinearLayout navigationItem(String title, int icon, Page page) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(0, dp(4), 0, dp(2));
        item.setBackground(navigationRipple());

        FrameLayout pill = new FrameLayout(this);
        pill.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(32)));
        item.addView(pill);

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(icon);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setImageTintList(ColorStateList.valueOf(MUTED));
        pill.addView(iconView, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));

        TextView labelView = label(title, 12, MUTED, Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(3);
        item.addView(labelView, labelParams);

        item.setOnClickListener(ignored -> showPage(page));
        navigationItems.add(new NavigationItemView(item, pill, iconView, labelView, page));
        return item;
    }

    /** 底部导航项的圆角涟漪背景，遮罩决定涟漪边界。 */
    private RippleDrawable navigationRipple() {
        GradientDrawable mask = rounded(Color.WHITE, 16, Color.TRANSPARENT, 0);
        return new RippleDrawable(ColorStateList.valueOf(navRipple), null, mask);
    }

    /** 应用栏圆形涟漪，贴近原生三点按钮的反馈。 */
    private RippleDrawable circularRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(navRipple), null, mask);
    }

    private void showPage(Page page) {
        if (pcmStatusPoller != null) pcmStatusPoller.stop();
        voBLEAuthenticationVisible = false;
        handler.removeCallbacks(voBLEAuthenticationRefresh);
        voBLEAuthenticationStatusView = null;
        voBLEPairingCodeView = null;
        voBLEDiagnosticsView = null;
        voBLEClearCredentialButton = null;
        pcmHealthTile = null;
        currentPage = page;
        handler.removeCallbacks(debugRefresh);
        bottomBar.setVisibility(View.VISIBLE);
        contentHost.removeAllViews();
        View content;
        switch (page) {
        case CALLS: content = callsPage(); break;
        case DEBUG: content = debugPage(); break;
        case SETTINGS: content = settingsPage(); break;
        case STATUS:
        default: content = statusPage(); break;
        }
        contentHost.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateNavigation();
        updateAppBar(page);
        if (page == Page.DEBUG && realtimeLogging) handler.post(debugRefresh);
        if (page == Page.STATUS && resumed) pcmStatusPoller.start();
    }

    private void updateNavigation() {
        for (NavigationItemView item : navigationItems) {
            boolean selected = item.page == currentPage;
            int color = selected ? PRIMARY : MUTED;
            item.pill.setBackground(selected
                    ? rounded(navActiveContainer, 16, Color.TRANSPARENT, 0)
                    : null);
            item.iconView.setImageTintList(ColorStateList.valueOf(color));
            item.labelView.setTextColor(color);
            item.labelView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    /** 应用栏标题随页面切换；主题色按钮全页可见，三点菜单只在状态与设置页出现。 */
    private void updateAppBar(Page page) {
        if (appBarTitle != null) appBarTitle.setText(pageTitleText(page));
        if (appBarTheme != null) appBarTheme.setVisibility(View.VISIBLE);
        if (appBarOverflow != null) {
            appBarOverflow.setVisibility(
                    page == Page.STATUS || page == Page.SETTINGS ? View.VISIBLE : View.GONE);
        }
    }

    private String pageTitleText(Page page) {
        return switch (page) {
            case CALLS -> "通话";
            case DEBUG -> "调试";
            case SETTINGS -> "设置";
            case STATUS -> "状态";
        };
    }

    /** 次级页面（配对、VoBLE 认证）不经过 showPage，需要手动同步应用栏标题并隐藏操作按钮。 */
    private void presentSubPage(String title) {
        if (appBarTitle != null) appBarTitle.setText(title);
        if (appBarTheme != null) appBarTheme.setVisibility(View.GONE);
        if (appBarOverflow != null) appBarOverflow.setVisibility(View.GONE);
    }

    /** 原生风格的三点溢出菜单：收纳低频危险操作，均保留二次确认。 */
    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        Menu items = menu.getMenu();
        items.add(Menu.NONE, 1, Menu.NONE, "清除调试日志");
        items.add(Menu.NONE, 2, Menu.NONE, "清除 AirChat 配对凭据");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                confirmClearDebugLog();
                return true;
            }
            if (item.getItemId() == 2) {
                confirmClearPairing();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmClearDebugLog() {
        new AlertDialog.Builder(this)
                .setTitle("清空调试日志？")
                .setMessage("将删除本机已记录的诊断日志；不影响通话、配对与云端中继。")
                .setPositiveButton("清空日志", (dialog, which) -> {
                    BridgeLog.clear();
                    refreshDebugLog();
                    toast("调试日志已清空");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClearPairing() {
        new AlertDialog.Builder(this)
                .setTitle("清除 AirChat 配对凭据？")
                .setMessage("将删除已保存的 VoWLAN 本地认证密钥与云端 Relay 注册凭据，并停止后台中继服务；之后 AirChat 需要重新配对才能控制通话。")
                .setPositiveButton("清除凭据", (dialog, which) -> clearPairingCredentials())
                .setNegativeButton("取消", null)
                .show();
    }

    /** LDev：强调色固定为海洋蓝，不再提供强调色预设；仅保留明暗主题切换。 */

    /** 顶部调色板按钮菜单：暗黑灰 / 亮白蓝 明暗主题切换。 */
    private void showThemeMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String mode = prefs.getString(THEME_MODE_KEY, "dark");
        menu.getMenu().add(Menu.NONE, 0, Menu.NONE,
                ("dark".equals(mode) ? "✓ " : "") + "暗黑灰主题");
        menu.getMenu().add(Menu.NONE, 1, Menu.NONE,
                ("light".equals(mode) ? "✓ " : "") + "亮白蓝主题");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0 || item.getItemId() == 1) {
                applyThemeMode(item.getItemId() == 1 ? "light" : "dark");
                return true;
            }
            return false;
        });
        menu.show();
    }

    /** 切换明暗主题（暗黑灰 / 亮白蓝），整体重建界面。 */
    private void applyThemeMode(String mode) {
        getPreferences(MODE_PRIVATE).edit().putString(THEME_MODE_KEY, mode).apply();
        loadThemeColors();
        rebuildForThemeChange();
        toast("light".equals(mode) ? "已切换到亮白蓝主题" : "已切换到暗黑灰主题");
    }

    /** 切换主题后整体重建界面；保留拨号/Agent 表单中尚未保存的输入。 */
    private void rebuildForThemeChange() {
        String savedNumber = number == null ? "" : number.getText().toString();
        String savedEndpoint = endpoint == null ? "" : endpoint.getText().toString();
        String savedToken = token == null ? "" : token.getText().toString();
        configureWindow();
        setContentView(buildShell());
        showPage(currentPage);
        if (!savedNumber.isEmpty() && number != null) number.setText(savedNumber);
        if (!savedEndpoint.isEmpty() && endpoint != null) endpoint.setText(savedEndpoint);
        if (!savedToken.isEmpty() && token != null) token.setText(savedToken);
    }

    private record DirectEndpoint(String type, String ip, boolean isTailscale) {}

    private List<DirectEndpoint> directEndpoints() {
        List<DirectEndpoint> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                if (!item.isUp() || item.isLoopback()) continue;
                String ifName = item.getName() == null ? "" : item.getName().toLowerCase(java.util.Locale.ROOT);
                Enumeration<InetAddress> addresses = item.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address)) continue;
                    String host = address.getHostAddress();
                    if (host == null || host.startsWith("127.")) continue;

                    // 上游 v0.9.7.2：Tailscale 判定统一走 isOverlayIPv4（含 tun/tailscale 接口）。
                    boolean isTailscale = VoWLANNetworkPolicy.isOverlayIPv4(host)
                            || VoWLANNetworkPolicy.isOverlayInterface(ifName, host)
                            || ifName.startsWith("wg");
                    boolean isHotspot = VoWLANNetworkPolicy.isHotspotInterface(ifName, host);
                    boolean isWiFi = ifName.startsWith("wlan");
                    boolean isPrivate = VoWLANNetworkPolicy.isPrivateIPv4(host);

                    if (isTailscale) {
                        list.add(new DirectEndpoint("Tailscale VPN", host, true));
                    } else if (isHotspot) {
                        list.add(new DirectEndpoint("热点 AP", host, false));
                    } else if (isWiFi) {
                        list.add(new DirectEndpoint("Wi-Fi 局域网", host, false));
                    } else if (isPrivate) {
                        list.add(new DirectEndpoint("局域网", host, false));
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private LinearLayout iosDirectEndpointCard() {
        LinearLayout card = card(SURFACE, 18);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout headerRow = row();
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("AirChat 直连模式", 17, CYAN, Gravity.START);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(title, weighted());

        TextView badge = label("三模", 11, BG, Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(rounded(CYAN, 8, Color.TRANSPARENT, 0));
        badge.setPadding(dp(7), dp(2), dp(7), dp(2));
        headerRow.addView(badge);
        card.addView(headerRow);

        int controlPort = VoWLANGatewayService.activeControlPort();
        int pcmPort = VoWLANGatewayService.activePcmPort();

        TextView devLabel = label("本机网关设备名: " + AndroidAgentIdentity.deviceName(), 13, TEXT, Gravity.START);
        devLabel.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(devLabel, matchWithTop(6));

        List<DirectEndpoint> endpoints = directEndpoints();
        if (endpoints.isEmpty()) {
            TextView emptyText = label("未检测到活跃的 Wi-Fi 或 Tailscale IPv4 连接\n请连接 Wi-Fi 或启动 Tailscale 虚拟网", 13, WARNING, Gravity.START);
            emptyText.setLineSpacing(dp(2), 1f);
            card.addView(emptyText, matchWithTop(8));
        } else {
            card.addView(overline("直连 IP 地址（点击复制直连 URL）"), matchWithTop(10));
            for (DirectEndpoint ep : endpoints) {
                LinearLayout ipRow = card(SURFACE_HIGH, 12);
                ipRow.setOrientation(LinearLayout.HORIZONTAL);
                ipRow.setGravity(Gravity.CENTER_VERTICAL);
                ipRow.setPadding(dp(12), dp(10), dp(12), dp(10));

                TextView typeTag = label(ep.type, 11, ep.isTailscale ? PRIMARY : BLUE, Gravity.CENTER);
                typeTag.setTypeface(Typeface.DEFAULT_BOLD);
                typeTag.setBackground(rounded(Color.argb(40, 255, 255, 255), 6, Color.TRANSPARENT, 0));
                typeTag.setPadding(dp(6), dp(2), dp(6), dp(2));
                ipRow.addView(typeTag);

                TextView ipView = label(ep.ip, 15, TEXT, Gravity.START);
                ipView.setTypeface(Typeface.MONOSPACE);
                LinearLayout.LayoutParams ipParams = weighted();
                ipParams.leftMargin = dp(10);
                ipRow.addView(ipView, ipParams);

                TextView copyBtn = label("复制", 12, CYAN, Gravity.CENTER);
                copyBtn.setTypeface(Typeface.DEFAULT_BOLD);
                copyBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
                copyBtn.setBackground(rounded(SURFACE, 8, BORDER, 1));
                ipRow.addView(copyBtn);

                final String copyUrl = "http://" + ep.ip + ":" + controlPort;
                ipRow.setClickable(true);
                ipRow.setOnClickListener(v -> {
                    getSystemService(ClipboardManager.class).setPrimaryClip(
                            ClipData.newPlainText("AirSIM Direct URL", copyUrl));
                    toast("已复制直连地址: " + copyUrl);
                });

                card.addView(ipRow, matchWithTop(6));
            }
        }

        LinearLayout portRow = row();
        portRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView portInfo = label("控制面 HTTP: " + controlPort + "   |   通话音频 PCM: " + pcmPort, 12, MUTED, Gravity.START);
        portInfo.setTypeface(Typeface.MONOSPACE);
        portRow.addView(portInfo, weighted());
        card.addView(portRow, matchWithTop(10));

        TextView guide = description("在 macOS 端、iOS 端和安卓端的 AirChat 设置中输入上方 IP 及端口，即可纯离线双向通话及收发短信，免 APNs / 云端 Relay。");
        card.addView(guide, matchWithTop(6));

        boolean hasPairing = hasStoredPairingCredentials();
        if (hasPairing) {
            // LDev：解除配对入口统一移到「设置 → 配对连接」，直连端点卡只读。
        }

        return card;
    }

    private LinearLayout simSlotsCard() {
        LinearLayout card = card(SURFACE, 18);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout headerRow = row();
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("DSDS 动态双卡聚合卡槽", 17, PRIMARY, Gravity.START);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(title, weighted());

        TextView badge = label("聚合网关", 11, BG, Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(rounded(PRIMARY, 8, Color.TRANSPARENT, 0));
        badge.setPadding(dp(7), dp(2), dp(7), dp(2));
        headerRow.addView(badge);
        card.addView(headerRow);

        card.addView(description("单端口聚合模式 · 实时探测卡槽状态并路由呼叫/短信"), matchWithTop(4));

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            TextView warning = label("尚未授予「电话状态」权限，无法探测双卡拓扑与运营商状态", 13, WARNING, Gravity.START);
            card.addView(warning, matchWithTop(8));
            TextView grantBtn = secondaryButton("授权读取电话状态");
            grantBtn.setOnClickListener(v -> requestPermissions(
                    new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS}, 104));
            card.addView(grantBtn, matchWithTop(10));
            return card;
        }

        List<SIMSlotInfo> slots = SIMSlotManager.getActiveSlots(this);
        if (slots.isEmpty()) {
            TextView emptyText = label("未检测到已插入的 SIM 卡或活跃的 eSIM 配置文件", 13, MUTED, Gravity.START);
            card.addView(emptyText, matchWithTop(8));
        } else {
            for (SIMSlotInfo slot : slots) {
                LinearLayout slotRow = card(SURFACE_HIGH, 12);
                slotRow.setOrientation(LinearLayout.VERTICAL);
                slotRow.setPadding(dp(12), dp(10), dp(12), dp(10));

                LinearLayout topRow = row();
                topRow.setGravity(Gravity.CENTER_VERTICAL);

                String slotTitle = (slot.isEmbedded() ? "eSIM" : "卡槽 " + (slot.slotIndex() + 1))
                        + " · " + (slot.carrierName().isEmpty() ? "未知运营商" : slot.carrierName());
                TextView slotLabel = label(slotTitle, 15, TEXT, Gravity.START);
                slotLabel.setTypeface(Typeface.DEFAULT_BOLD);
                topRow.addView(slotLabel, weighted());

                LinearLayout badges = row();
                badges.setGravity(Gravity.CENTER_VERTICAL);

                if (slot.isDefaultVoice()) {
                    TextView defVoice = label("默认通话", 10, BG, Gravity.CENTER);
                    defVoice.setBackground(rounded(CYAN, 6, Color.TRANSPARENT, 0));
                    defVoice.setPadding(dp(5), dp(1), dp(5), dp(1));
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    bp.leftMargin = dp(4);
                    badges.addView(defVoice, bp);
                }
                if (slot.isDefaultSms()) {
                    TextView defSms = label("默认短信", 10, BG, Gravity.CENTER);
                    defSms.setBackground(rounded(BLUE, 6, Color.TRANSPARENT, 0));
                    defSms.setPadding(dp(5), dp(1), dp(5), dp(1));
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    bp.leftMargin = dp(4);
                    badges.addView(defSms, bp);
                }

                String stateText = "ready".equalsIgnoreCase(slot.simState()) ? "正常就绪" : slot.simState();
                int stateColor = "ready".equalsIgnoreCase(slot.simState()) ? PRIMARY : WARNING;
                TextView stateBadge = label(stateText, 10, stateColor, Gravity.CENTER);
                stateBadge.setBackground(rounded(Color.argb(35, 255, 255, 255), 6, Color.TRANSPARENT, 0));
                stateBadge.setPadding(dp(5), dp(1), dp(5), dp(1));
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                sp.leftMargin = dp(4);
                badges.addView(stateBadge, sp);

                topRow.addView(badges);
                slotRow.addView(topRow);

                String num = slot.number().isEmpty() ? "运营商未提供本机号码" : slot.number();
                String details = "号码: " + num + "  |  Sub ID: " + slot.subscriptionId();
                TextView detailsView = label(details, 12, MUTED, Gravity.START);
                detailsView.setTypeface(Typeface.MONOSPACE);
                slotRow.addView(detailsView, matchWithTop(4));

                card.addView(slotRow, matchWithTop(8));
            }
        }

        TextView summary = label("已探测到 " + slots.size() + " 个活跃卡槽 · 支持动态热插拔识别", 12, MUTED, Gravity.START);
        card.addView(summary, matchWithTop(8));

        return card;
    }

    private View statusPage() {
        LinearLayout root = pageRoot();
        String vName = appVersionName();
        if (!vName.startsWith("v") && !vName.startsWith("V")) vName = "v" + vName;
        root.addView(pageTitle("AirSIM", vName + " · 运行中"));

        boolean paired = VoWLANPairingStore.configured(this);
        // LDev：hero 区块 = AirChat 连接状态。未配对显示「AirChat 尚未连接」并点击进配对页；
        // 已配对则展示当前连接模式（原「当前连接模式」小区块合并至此，避免重复）。
        boolean ready = vowlanReady();
        LinearLayout hero = card(SURFACE_HIGH, 24);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(20), dp(18), dp(20));
        ImageView wifi = icon(android.R.drawable.ic_menu_share, ready ? CYAN : WARNING, 54);
        wifi.setBackground(rounded(ready ? tintContainer : warnContainer, 28, Color.TRANSPARENT, 0));
        wifi.setPadding(dp(14), dp(14), dp(14), dp(14));
        hero.addView(wifi, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout heroCopy = column(dp(14));
        if (!paired) {
            TextView heroTitle = label("AirChat 尚未连接", 25, WARNING, Gravity.START);
            heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
            heroCopy.addView(heroTitle);
            heroCopy.addView(label("点击开始配对 · " + vowlanNetworkLine(), 14, MUTED, Gravity.START));
            hero.setOnClickListener(ignored -> openPairingPage());
        } else {
            String connectionMode = MainScreenPresentation.connectionModeTitle(
                    ready, cloudEnabled, cloudRelayConfigured, cloudLastError);
            String connectionDetail = MainScreenPresentation.connectionModeDetail(
                    ready, cloudEnabled, cloudRelayConfigured, cloudLastError);
            int connectionColor = ready ? CYAN
                    : cloudEnabled && cloudRelayConfigured && cloudLastError.isEmpty() ? BLUE : WARNING;
            TextView heroTitle = label(connectionMode, 25, connectionColor, Gravity.START);
            heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
            heroCopy.addView(heroTitle);
            heroCopy.addView(label(connectionDetail, 14, MUTED, Gravity.START));
            hero.setOnClickListener(ignored -> showFeatureDetails(Feature.VOWLAN));
        }
        hero.setClickable(true);
        hero.addView(heroCopy, weighted());
        root.addView(hero, matchWithTop(16));

        root.addView(iosDirectEndpointCard(), matchWithTop(14));
        root.addView(simSlotsCard(), matchWithTop(14));

        // LDev：原「当前连接模式」小区块已合并进顶部 AirChat 连接状态区块，不再重复渲染。

        // 蓝牙诊断卡只在 manifest 声明 VoBLE 时显示；Lite 变体没有这项能力。
        if (FeatureFlags.voBLEAvailable(this)) {
            LinearLayout bluetoothCard = card(SURFACE, 18);
            bluetoothCard.setOrientation(LinearLayout.VERTICAL);
            bluetoothCard.setPadding(dp(16), dp(14), dp(16), dp(14));
            bluetoothCard.addView(overline("蓝牙通话（诊断已脱敏）"));
            bluetoothCard.addView(label(
                    com.airsim.phonecontrol.voble.VoBLEGatewayService.diagnosticsSummary(),
                    14, MUTED, Gravity.START), matchWithTop(5));
            root.addView(bluetoothCard, matchWithTop(10));
        }

        LinearLayout firstHealthRow = row();
        HealthTile agentTile = healthTile(
                RuntimeMode.agentLabel(this), agentOnline ? "在线" : agentDetail,
                android.R.drawable.ic_menu_manage, agentOnline);
        agentTile.view.setOnClickListener(ignored -> showFeatureDetails(Feature.AGENT));
        firstHealthRow.addView(agentTile.view, weightedWithEnd(5));
        pcmHealthTile = healthTile(
                "PCM 音频桥", pcmBridgeRunning() ? "运行中" : "待启动",
                android.R.drawable.ic_media_play, pcmBridgeRunning());
        pcmHealthTile.view.setOnClickListener(ignored -> showFeatureDetails(Feature.PCM));
        firstHealthRow.addView(pcmHealthTile.view, weightedWithStart(5));
        root.addView(firstHealthRow, matchWithTop(12));

        LinearLayout secondHealthRow = row();
        HealthTile pushTile = healthTile(
                "Push / Relay", pushDetail,
                android.R.drawable.stat_notify_sync, pushConfigured);
        pushTile.view.setOnClickListener(ignored -> showFeatureDetails(Feature.PUSH));
        secondHealthRow.addView(pushTile.view, weightedWithEnd(5));
        HealthTile shizukuTile = healthTile(
                "Shizuku", shizukuReady() ? "已授权" : "待授权",
                android.R.drawable.ic_lock_idle_lock, shizukuReady());
        shizukuTile.view.setOnClickListener(ignored -> showFeatureDetails(Feature.SHIZUKU));
        secondHealthRow.addView(shizukuTile.view, weightedWithStart(5));
        root.addView(secondHealthRow, matchWithTop(10));

        root.addView(modeSelector(), matchWithTop(14));

        // LDev：状态页只展示状态；配对入口统一移到「设置 → 配对连接」。
        // 原状态页的「重新配对 AirChat / 解除配对」按钮已迁移至设置页配对分组。

        root.addView(sectionTitle("最近活动"), matchWithTop(22));
        LinearLayout recent = card(SURFACE, 18);
        recent.setPadding(dp(16), dp(14), dp(16), dp(14));
        recent.setOrientation(LinearLayout.HORIZONTAL);
        recent.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(rounded(PRIMARY, 7, Color.TRANSPARENT, 0));
        recent.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView activity = label(latestActivity(), 14, TEXT, Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams activityParams = weighted();
        activityParams.leftMargin = dp(12);
        recent.addView(activity, activityParams);
        recent.setOnClickListener(ignored -> showPage(Page.DEBUG));
        root.addView(recent, matchWithTop(10));

        status = label("", 12, MUTED, Gravity.START);
        status.setVisibility(View.GONE);
        root.addView(status);
        return scroll(root);
    }

    private View callsPage() {
        LinearLayout root = pageRoot();
        // 页面标题由顶部应用栏承载，页内不再重复渲染大标题。
        root.addView(description("Telecom 本机控制；AirChat 通话优先使用 VoWLAN/Tailscale，局域网不可用时由云端中继。"), matchWithTop(8));

        LinearLayout dialCard = card(SURFACE, 24);
        dialCard.setOrientation(LinearLayout.VERTICAL);
        dialCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        dialCard.addView(overline("拨号号码"));
        number = input("输入电话号码", InputType.TYPE_CLASS_PHONE);
        dialCard.addView(number, matchWithTop(8));

        List<SIMSlotInfo> slots = SIMSlotManager.getActiveSlots(this);
        if (slots.size() > 1) {
            dialCard.addView(overline("外呼卡槽分发"), matchWithTop(10));
            LinearLayout slotSelector = row();
            slotSelector.setGravity(Gravity.CENTER_VERTICAL);

            TextView autoBtn = label("跟随系统默认", 12, selectedDialSlot == -1 ? PRIMARY : MUTED, Gravity.CENTER);
            autoBtn.setBackground(rounded(selectedDialSlot == -1 ? Color.argb(45, 52, 229, 170) : SURFACE_HIGH,
                    10, selectedDialSlot == -1 ? PRIMARY : BORDER, 1));
            autoBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
            autoBtn.setClickable(true);
            autoBtn.setOnClickListener(v -> {
                selectedDialSlot = -1;
                showPage(Page.CALLS);
            });
            slotSelector.addView(autoBtn, weightedWithEnd(4));

            for (SIMSlotInfo slot : slots) {
                int sIdx = slot.slotIndex();
                boolean isSelected = (selectedDialSlot == sIdx);
                String btnText = (slot.isEmbedded() ? "eSIM" : "SIM " + (sIdx + 1))
                        + (slot.carrierName().isEmpty() ? "" : " · " + slot.carrierName());
                TextView slotBtn = label(btnText, 12, isSelected ? PRIMARY : MUTED, Gravity.CENTER);
                slotBtn.setBackground(rounded(isSelected ? Color.argb(45, 52, 229, 170) : SURFACE_HIGH,
                        10, isSelected ? PRIMARY : BORDER, 1));
                slotBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
                slotBtn.setClickable(true);
                slotBtn.setOnClickListener(v -> {
                    selectedDialSlot = sIdx;
                    showPage(Page.CALLS);
                });
                slotSelector.addView(slotBtn, weightedWithEnd(4));
            }
            dialCard.addView(slotSelector, matchWithTop(6));
        }

        String callBtnText = "通过系统 Telecom 拨号"
                + (selectedDialSlot >= 0 ? " (卡槽 " + (selectedDialSlot + 1) + ")" : "");
        TextView call = primaryButton(callBtnText, PRIMARY);
        call.setTextColor(BG);
        call.setOnClickListener(ignored -> placeSystemCall());
        dialCard.addView(call, matchWithTop(14));
        root.addView(dialCard, matchWithTop(18));

        root.addView(sectionTitle("当前通话"), matchWithTop(22));
        LinearLayout actions = row();
        TextView answer = secondaryButton("接听");
        answer.setOnClickListener(ignored -> localCall("answer"));
        TextView hangup = secondaryButton("挂断通话");
        hangup.setTextColor(DANGER);
        hangup.setOnClickListener(ignored -> localCall("end"));
        actions.addView(answer, weightedWithEnd(5));
        actions.addView(hangup, weightedWithStart(5));
        root.addView(actions, matchWithTop(10));

        // 以下两块由设置页迁移至此（底部）：默认通话卡、电话角色与诊断。
        // 原角色提示与“设为默认电话应用”入口已合并进诊断卡，功能不减。
        root.addView(sectionTitle("默认通话卡"), matchWithTop(22));
        root.addView(defaultCallAccountCard(), matchWithTop(10));

        root.addView(sectionTitle("电话角色与诊断"), matchWithTop(22));
        root.addView(phoneRoleDiagnosticsCard(), matchWithTop(10));
        return scroll(root);
    }

    /** 默认通话卡（原设置页区块迁移）：AirSIM 拨号使用的 SIM/eSIM 选择。 */
    private LinearLayout defaultCallAccountCard() {
        LinearLayout callAccountCard = card(SURFACE, 22);
        callAccountCard.setOrientation(LinearLayout.VERTICAL);
        callAccountCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        DefaultCallAccount.Snapshot accounts = DefaultCallAccount.snapshot(this);
        String accountKey = DefaultCallAccount.selectedKey(this);
        boolean available = accountKey.isEmpty() || accounts.accounts().stream()
                .anyMatch(account -> account.key().equals(accountKey));
        callAccountCard.addView(label(DefaultCallAccount.selectedLabel(this)
                + (available ? "" : " · 当前不可用"), 14, available ? TEXT : WARNING, Gravity.START));
        callAccountCard.addView(description("用于 AirSIM 蓝牙、VoWLAN 与云端拨号，不改变系统通话卡或短信卡。"
                + "指定卡关闭或移除时会停止拨号，请重新选择。"), matchWithTop(6));
        if (!accounts.error().isEmpty()) {
            callAccountCard.addView(description(accounts.error()), matchWithTop(6));
        } else if (accounts.accounts().isEmpty()) {
            callAccountCard.addView(description("没有已启用的蜂窝通话卡"), matchWithTop(6));
        }
        TextView chooseAccount = secondaryButton("选择默认通话卡");
        chooseAccount.setOnClickListener(ignored -> chooseDefaultCallAccount());
        callAccountCard.addView(chooseAccount, matchWithTop(10));
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            TextView grantAccountPermission = secondaryButton("授权读取通话卡");
            grantAccountPermission.setOnClickListener(ignored -> requestPermissions(
                    new String[] {Manifest.permission.READ_PHONE_STATE}, CALL_ACCOUNT_PERMISSION_REQUEST));
            callAccountCard.addView(grantAccountPermission, matchWithTop(10));
        }
        return callAccountCard;
    }

    /** 电话角色与诊断（原设置页区块迁移）：默认电话角色、Agent 刷新与 Debug 开关。 */
    private LinearLayout phoneRoleDiagnosticsCard() {
        LinearLayout roleCard = card(SURFACE, 22);
        roleCard.setOrientation(LinearLayout.VERTICAL);
        roleCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        roleCard.addView(label(roleText(), 14, TEXT, Gravity.START));
        roleCard.addView(description("紧急呼叫始终由系统预装拨号器处理。"), matchWithTop(4));

        LinearLayout roleActions = row();
        TextView dialer = secondaryButton("设为默认电话应用");
        dialer.setOnClickListener(ignored -> requestDialerRole());
        roleActions.addView(dialer, weightedWithEnd(5));
        TextView agent = secondaryButton("刷新 Agent 状态");
        agent.setOnClickListener(ignored -> refreshAgent(true));
        roleActions.addView(agent, weightedWithStart(5));
        roleCard.addView(roleActions, matchWithTop(10));

        agentRefreshResult = label(agentRefreshMessage, 12, agentRefreshColor, Gravity.START);
        agentRefreshResult.setPadding(dp(12), dp(8), dp(12), dp(8));
        agentRefreshResult.setBackground(rounded(SURFACE, 12, BORDER, 1));
        roleCard.addView(agentRefreshResult, matchWithTop(8));

        CheckBox debugEnabled = new CheckBox(this);
        debugEnabled.setText("启用 Debug 模式");
        debugEnabled.setTextColor(TEXT);
        debugEnabled.setButtonTintList(ColorStateList.valueOf(PRIMARY));
        debugEnabled.setChecked(AppConfig.debugEnabled(this));
        debugEnabled.setOnCheckedChangeListener((ignored, enabled) -> {
            BridgeLog.setDebugEnabled(this, enabled);
            toast(enabled ? "Debug 日志已开启" : "Debug 日志已关闭");
        });
        roleCard.addView(debugEnabled, matchWithTop(12));
        return roleCard;
    }

    private View debugPage() {
        LinearLayout root = pageRoot();
        // 页面标题由顶部应用栏承载，页内不再重复渲染大标题。
		if (CrashReportStore.hasReport(getFilesDir())) {
			LinearLayout crashCard = card(Color.argb(38, 255, 92, 92), 22);
			crashCard.setOrientation(LinearLayout.VERTICAL);
			crashCard.setPadding(dp(16), dp(15), dp(16), dp(15));
			TextView title = label("检测到上次闪退", 16, DANGER, Gravity.START);
			title.setTypeface(Typeface.DEFAULT_BOLD);
			crashCard.addView(title);
			crashCard.addView(label("报告已脱敏并保存在本机，只有点击发送后才会离开设备。",
					12, MUTED, Gravity.START), matchWithTop(4));
			LinearLayout crashActions = row();
			TextView send = secondaryButton("发送闪退报告");
			send.setTextColor(PRIMARY);
			send.setOnClickListener(ignored -> shareCrashReport());
			TextView remove = secondaryButton("删除报告");
			remove.setTextColor(DANGER);
			remove.setOnClickListener(ignored -> {
				CrashReportStore.clear(getFilesDir());
				toast("闪退报告已删除");
				showPage(Page.DEBUG);
			});
			crashActions.addView(send, weightedWithEnd(5));
			crashActions.addView(remove, weightedWithStart(5));
			crashCard.addView(crashActions, matchWithTop(12));
			root.addView(crashCard, matchWithTop(12));
		}
        root.addView(debugFilters(), matchWithTop(14));

        LinearLayout logCard = card(SURFACE, 22);
        logCard.setOrientation(LinearLayout.VERTICAL);
        logCard.setPadding(dp(12), dp(4), dp(12), dp(4));
        debugRows = column(0);
        lastRenderedDebugLog = null;
        lastRenderedDebugFilter = null;
        logCard.addView(debugRows);
        root.addView(logCard, matchWithTop(14));

        LinearLayout actions = row();
        TextView copy = secondaryButton("复制日志");
        copy.setOnClickListener(ignored -> copyDebugLog());
        TextView export = secondaryButton("导出诊断");
        export.setOnClickListener(ignored -> exportDebugLog());
        actions.addView(copy, weightedWithEnd(5));
        actions.addView(export, weightedWithStart(5));
        root.addView(actions, matchWithTop(12));

        TextView watchdogExport = secondaryButton("保存音频 Watchdog TXT");
        watchdogExport.setOnClickListener(ignored -> {
            Intent document = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            document.addCategory(Intent.CATEGORY_OPENABLE);
            document.setType("text/plain");
            document.putExtra(Intent.EXTRA_TITLE, "AirSIM-audio-watchdog-" + System.currentTimeMillis() + ".txt");
            try { startActivityForResult(document, DIAGNOSTIC_TXT_REQUEST); }
            catch (RuntimeException error) { toast("无法打开文件保存窗口，请使用复制日志"); }
        });
        root.addView(watchdogExport, matchWithTop(8));
        root.addView(label("远程通话时每 2 秒复检静音状态，受控修复后再次验证。脱敏 VERBOSE 快照自动保存在本机；系统标志不能保证实际扬声器无声。",
                12, MUTED, Gravity.START), matchWithTop(8));

        LinearLayout live = card(SURFACE, 20);
        live.setOrientation(LinearLayout.HORIZONTAL);
        live.setGravity(Gravity.CENTER_VERTICAL);
        live.setPadding(dp(16), dp(14), dp(12), dp(14));
        LinearLayout liveCopy = column(0);
        liveCopy.addView(label("实时记录", 15, TEXT, Gravity.START));
        liveCopy.addView(label("自动刷新最新的日志信息", 12, MUTED, Gravity.START));
        live.addView(liveCopy, weighted());
        Switch toggle = new Switch(this);
        toggle.setChecked(realtimeLogging);
        toggle.setThumbTintList(ColorStateList.valueOf(PRIMARY));
        toggle.setOnCheckedChangeListener((ignored, enabled) -> {
            realtimeLogging = enabled;
            handler.removeCallbacks(debugRefresh);
            if (enabled) handler.post(debugRefresh);
        });
        live.addView(toggle);
        root.addView(live, matchWithTop(12));

        LinearLayout destructive = row();
        TextView refresh = secondaryButton("立即刷新");
        refresh.setOnClickListener(ignored -> refreshDebugLog());
        TextView clear = secondaryButton("清空日志");
        clear.setTextColor(DANGER);
        clear.setOnClickListener(ignored -> { BridgeLog.clear(); refreshDebugLog(); });
        destructive.addView(refresh, weightedWithEnd(5));
        destructive.addView(clear, weightedWithStart(5));
        root.addView(destructive, matchWithTop(10));
        refreshDebugLog();
        return scroll(root);
    }

    private View settingsPage() {
        LinearLayout root = pageRoot();
        // 页面标题由顶部应用栏承载，页内不再重复渲染大标题。
        // LDev：「配对连接」「外观」分组已去除（与溢出菜单/调色板按钮重复）；
        // 配对入口 = AirChat 配对凭证卡片 + VoBLE 蓝牙配对卡片；主题切换 = 顶部调色板按钮。

        boolean standaloneRuntime = RuntimeMode.isStandalone(this);

        root.addView(sectionTitle("AirChat 配对凭证"), matchWithTop(18));
        LinearLayout pairingCredentialCard = card(SURFACE, 22);
        pairingCredentialCard.setOrientation(LinearLayout.VERTICAL);
        pairingCredentialCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        boolean hasPairing = hasStoredPairingCredentials();
        pairingCredentialCard.addView(label(hasPairing ? "已保存 AirChat 配对凭据（VoWLAN 与 Relay）" : "尚未保存配对凭据",
                14, hasPairing ? PRIMARY : MUTED, Gravity.START));
        pairingCredentialCard.addView(description("清除后将删除 VoWLAN 本地认证密钥与云端 Relay 注册凭据，并断开中继连接；AirChat 需要重新配对才能呼叫。"),
                matchWithTop(6));
        // LDev：清除凭据 = 红底白字实心按钮。
        TextView clearPairingBtn = dangerButton("清除 AirChat 配对凭据");
        clearPairingBtn.setEnabled(hasPairing);
        // LDev：清除按钮统一纯红底；禁用仅拦截点击，不做半透明淡化。
        clearPairingBtn.setOnClickListener(ignored -> confirmClearPairing());
        pairingCredentialCard.addView(clearPairingBtn, matchWithTop(10));
        // LDev：新增重新配对入口（原状态页「配对 AirChat」按钮迁移至此）。
        TextView rePairBtn = primaryButton("重新配对 AirChat", BLUE);
        rePairBtn.setOnClickListener(ignored -> openPairingPage());
        pairingCredentialCard.addView(rePairBtn, matchWithTop(10));
        root.addView(pairingCredentialCard, matchWithTop(10));

        // 能力探测：Lite 变体的 manifest 不声明 VoBLE 服务，因此整块蓝牙 UI 必须隐藏，
        // 而不是显示一个点了就报错的入口。两种变体共用同一份源码。
        if (FeatureFlags.voBLEAvailable(this)) {
            root.addView(sectionTitle("VoBLE 蓝牙配对"), matchWithTop(22));
            LinearLayout voBLECard = card(SURFACE, 22);
            voBLECard.setOrientation(LinearLayout.VERTICAL);
            voBLECard.setPadding(dp(16), dp(16), dp(16), dp(16));
            boolean voBLEPaired = com.airsim.phonecontrol.voble.VoBLEGatewayService
                    .hasStoredCredential(this);
            voBLECard.addView(label(voBLEPaired ? "已保存 AirChat 认证凭据" : "尚未保存认证凭据",
                    14, voBLEPaired ? PRIMARY : MUTED, Gravity.START));
            voBLECard.addView(description("管理六位安全码认证、旧凭据恢复与双端取消配对。"),
                    matchWithTop(6));
            // LDev：打开 VoBLE 配置 = 蓝底白字；清除 VoBLE 凭据 = 红底白字。
            TextView configureVoBLE = primaryButton("打开 VoBLE 配对配置", BLUE);
            configureVoBLE.setOnClickListener(ignored -> openVoBLEAuthenticationPage());
            voBLECard.addView(configureVoBLE, matchWithTop(10));
            TextView clearVoBLEBtn = dangerButton("清除 VoBLE 配对凭据");
            clearVoBLEBtn.setEnabled(voBLEPaired);
            clearVoBLEBtn.setOnClickListener(ignored -> new AlertDialog.Builder(this)
                    .setTitle("清除旧的 VoBLE 配对？")
                    .setMessage("仅删除蓝牙认证密钥并结束当前 VoBLE 会话；不会清除 VoWLAN、Relay 或通话记录。")
                    .setPositiveButton("清除并重新配对", (dialog, which) -> {
                        com.airsim.phonecontrol.voble.VoBLEGatewayService.clearStoredCredential(this);
                        toast("VoBLE 旧凭据已清除");
                    })
                    .setNegativeButton("取消", null)
                    .show());
            voBLECard.addView(clearVoBLEBtn, matchWithTop(10));
            root.addView(voBLECard, matchWithTop(10));
        }

        // LDev：内置 Agent 区块下移至 VoBLE 蓝牙配对之后。
        root.addView(sectionTitle(RuntimeMode.agentLabel(this)), matchWithTop(22));
        LinearLayout agentCard = card(SURFACE, 22);
        agentCard.setOrientation(LinearLayout.VERTICAL);
        agentCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        if (standaloneRuntime) {
            agentCard.addView(label("Agent 运行在本 APK 的前台服务中，不使用 AVF、Linux Terminal、8575 或 8576。",
                    14, TEXT, Gravity.START));
        } else {
            endpoint = input("Agent 地址", InputType.TYPE_CLASS_TEXT);
            endpoint.setText(AppConfig.endpoint(this));
            agentCard.addView(endpoint);
            token = input("控制令牌", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            token.setText(AppConfig.token(this));
            agentCard.addView(token, matchWithTop(10));
        }
        selectedMode = AppConfig.mode(this);
        agentCard.addView(modeSelector(), matchWithTop(12));
        TextView save = primaryButton(standaloneRuntime ? "保存模式并启动内置 Agent" : "保存配置并启动守护", BLUE);
        save.setOnClickListener(ignored -> saveConfiguration());
        agentCard.addView(save, matchWithTop(12));
        root.addView(agentCard, matchWithTop(10));

        // 「默认通话卡」与「电话角色与诊断」已迁移到通话页底部。

		root.addView(sectionTitle("短信收件箱兜底"), matchWithTop(22));
		LinearLayout smsCard = card(SURFACE, 22);
		smsCard.setOrientation(LinearLayout.VERTICAL);
		smsCard.setPadding(dp(16), dp(16), dp(16), dp(16));
		boolean inboxGranted = checkSelfPermission(Manifest.permission.READ_SMS)
				== PackageManager.PERMISSION_GRANTED;
		smsCard.addView(label(inboxGranted ? "已授权 · 仅扫描新收到的短信"
				: "未授权 · 继续使用短信广播接收", 14, TEXT, Gravity.START));
		smsCard.addView(description("可选权限；用于广播未送达时补取新短信，不导入历史短信。"), matchWithTop(6));
		if (!inboxGranted) {
			TextView permissionAction = secondaryButton("在系统设置中授权读取短信");
			permissionAction.setOnClickListener(ignored -> startActivity(new Intent(
					Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
					Uri.parse("package:" + getPackageName()))));
			smsCard.addView(permissionAction, matchWithTop(10));
		}
		root.addView(smsCard, matchWithTop(10));

		if (!standaloneRuntime) {
		root.addView(sectionTitle("AVF Linux 首次安装"), matchWithTop(22));
		LinearLayout bootstrapCard = card(SURFACE, 22);
		bootstrapCard.setOrientation(LinearLayout.VERTICAL);
		bootstrapCard.setPadding(dp(16), dp(16), dp(16), dp(16));
		AVFEnvironmentDetector.Snapshot avf = AVFEnvironmentDetector.inspect(this);
		bootstrapCard.addView(label(avfEnvironmentSummary(avf), 14, TEXT, Gravity.START));
		TextView installCommand = label(AVFStartupPolicy.installCommand(), 12, MUTED, Gravity.START);
		installCommand.setTextIsSelectable(true);
		installCommand.setPadding(0, dp(10), 0, dp(4));
		bootstrapCard.addView(installCommand);
		LinearLayout bootstrapActions = row();
		TextView copyInstall = secondaryButton("复制一键安装命令");
		copyInstall.setOnClickListener(ignored -> copyAVFInstallCommand());
		TextView openLinux = secondaryButton(avf.state() == AVFStartupPolicy.State.RUNNING
				? "打开 Linux Terminal" : "启动 Linux Terminal");
		openLinux.setOnClickListener(ignored -> openAVFTerminalOrSettings());
		bootstrapActions.addView(copyInstall, weightedWithEnd(5));
		bootstrapActions.addView(openLinux, weightedWithStart(5));
		bootstrapCard.addView(bootstrapActions, matchWithTop(10));
		TextView developerOptions = secondaryButton("打开开发者选项");
		developerOptions.setOnClickListener(ignored -> AVFEnvironmentActions.openDeveloperOptions(this));
		bootstrapCard.addView(developerOptions, matchWithTop(10));
		root.addView(bootstrapCard, matchWithTop(10));

		root.addView(sectionTitle("AVF Agent 管理"), matchWithTop(22));
		LinearLayout installerCard = card(SURFACE, 22);
		installerCard.setOrientation(LinearLayout.VERTICAL);
		installerCard.setPadding(dp(16), dp(16), dp(16), dp(16));
		installerStatus = label("正在读取 installerd 状态…", 13, MUTED, Gravity.START);
		installerCard.addView(installerStatus);
		TextView updateAgent = primaryButton("检查并安装最新 Agent", BLUE);
		updateAgent.setOnClickListener(ignored -> runAgentMaintenance("update"));
		installerCard.addView(updateAgent, matchWithTop(12));
		LinearLayout installerActions = row();
		TextView repairAgent = secondaryButton("修复当前版本");
		repairAgent.setOnClickListener(ignored -> runAgentMaintenance("repair"));
		TextView rollbackAgent = secondaryButton("回滚上一版本");
		rollbackAgent.setTextColor(WARNING);
		rollbackAgent.setOnClickListener(ignored -> runAgentMaintenance("rollback"));
		installerActions.addView(repairAgent, weightedWithEnd(5));
		installerActions.addView(rollbackAgent, weightedWithStart(5));
		installerCard.addView(installerActions, matchWithTop(10));
		root.addView(installerCard, matchWithTop(10));
		refreshInstallerStatus();
		}

        root.addView(sectionTitle("Shizuku 权限与 PCM 桥"), matchWithTop(22));
        LinearLayout shizukuCard = card(SURFACE, 22);
        shizukuCard.setOrientation(LinearLayout.VERTICAL);
        shizukuCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        shizukuStatus = label("", 14, TEXT, Gravity.START);
        shizukuCard.addView(shizukuStatus);
        shizukuCard.addView(description("首次通过无线调试启动并授权；之后 AirSIM 会自动恢复 PCM 桥。"), matchWithTop(6));
        LinearLayout shizukuActions = row();
        TextView authorize = secondaryButton("授权并启动");
        authorize.setOnClickListener(ignored -> {
            ShizukuBridgeManager.get(this).requestPermission();
            refreshShizukuStatus();
        });
        TextView open = secondaryButton("打开 Shizuku");
        open.setOnClickListener(ignored -> {
            ShizukuBridgeManager.get(this).openShizuku();
            refreshShizukuStatus();
        });
        shizukuActions.addView(authorize, weightedWithEnd(5));
        shizukuActions.addView(open, weightedWithStart(5));
        shizukuCard.addView(shizukuActions, matchWithTop(12));
        root.addView(shizukuCard, matchWithTop(10));

        root.addView(sectionTitle("设备信息"), matchWithTop(22));
        LinearLayout deviceInfoCard = card(SURFACE, 22);
        deviceInfoCard.setOrientation(LinearLayout.VERTICAL);
        deviceInfoCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        deviceInfoCard.addView(description(
                "汇总本机型号、系统版本、Wi-Fi 与蓝牙能力，便于问题反馈。"
                        + "不含 IMEI、手机号、MAC 或设备标识；点击获取后可复制，是否粘贴反馈由你自行决定。"));
        TextView fetchDevice = secondaryButton("获取设备信息");
        fetchDevice.setTextColor(PRIMARY);
        fetchDevice.setOnClickListener(ignored -> renderDeviceInfo());
        deviceInfoCard.addView(fetchDevice, matchWithTop(10));
        deviceInfoText = label("", 12, TEXT, Gravity.START);
        deviceInfoText.setTypeface(Typeface.MONOSPACE);
        deviceInfoText.setTextIsSelectable(true);
        deviceInfoText.setPadding(dp(12), dp(10), dp(12), dp(10));
        deviceInfoText.setBackground(rounded(SURFACE_HIGH, 12, BORDER, 1));
        deviceInfoText.setVisibility(View.GONE);
        deviceInfoCard.addView(deviceInfoText, matchWithTop(10));
        deviceInfoCopyButton = secondaryButton("复制设备信息");
        deviceInfoCopyButton.setTextColor(PRIMARY);
        deviceInfoCopyButton.setVisibility(View.GONE);
        deviceInfoCopyButton.setOnClickListener(ignored -> copyDeviceInfo());
        deviceInfoCard.addView(deviceInfoCopyButton, matchWithTop(10));
        // 主题切换等场景重建页面时，若已生成过信息则恢复展示，不需要机主重新点击。
        if (!deviceInfoValue.isEmpty()) {
            deviceInfoText.setText(deviceInfoValue);
            deviceInfoText.setVisibility(View.VISIBLE);
            deviceInfoCopyButton.setVisibility(View.VISIBLE);
        }
        root.addView(deviceInfoCard, matchWithTop(10));

        root.addView(sectionTitle("关于"), matchWithTop(22));
        LinearLayout aboutCard = card(SURFACE, 22);
        aboutCard.setOrientation(LinearLayout.VERTICAL);
        aboutCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        String runtimeLabel = standaloneRuntime ? "Standalone 内置模式" : "AVF 模式";
        String vName = appVersionName();
        if (!vName.startsWith("v") && !vName.startsWith("V")) vName = "v" + vName;
        aboutCard.addView(label("AirSIM · " + vName, 15, TEXT, Gravity.START));
        aboutCard.addView(description("运行架构：" + runtimeLabel + " · 跨设备通话、短信和 VoWLAN"), matchWithTop(4));
        // 借鉴上游 v0.9.7.2：关于区块提供原项目 Releases 链接，便于对照升级说明。
        TextView releases = secondaryButton("查看原项目 Releases");
        releases.setOnClickListener(ignored -> startActivity(new Intent(
                Intent.ACTION_VIEW, Uri.parse("https://github.com/kai-wu-cortex/AirSIM/releases"))));
        aboutCard.addView(releases, matchWithTop(10));
        root.addView(aboutCard, matchWithTop(10));

        // 上游 v0.9.7.2 的“关于”区块（AppInformation 版本与仓库链接）已由 LDev
        // 版本卡与 Releases 按钮承载，不再重复渲染。
        refreshShizukuStatus();
        return scroll(root);
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "V0.9.7.2 LDev 911";
        }
    }

    // ==================== 设备信息区块 ====================

    /** 机主主动点击才生成；不自动上传任何内容，也不包含 IMEI、手机号、MAC 或设备标识。 */
    private void renderDeviceInfo() {
        deviceInfoValue = collectDeviceInfo();
        deviceInfoText.setText(deviceInfoValue);
        deviceInfoText.setVisibility(View.VISIBLE);
        deviceInfoCopyButton.setVisibility(View.VISIBLE);
    }

    /** 复制到剪贴板即止；是否粘贴、发送给谁，完全由机主自行决定。 */
    private void copyDeviceInfo() {
        if (deviceInfoValue == null || deviceInfoValue.isEmpty()) return;
        getSystemService(ClipboardManager.class).setPrimaryClip(
                ClipData.newPlainText("AirSIM 设备信息", deviceInfoValue));
        toast("设备信息已复制；是否粘贴反馈由你决定");
    }

    private String collectDeviceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("== AirSIM 设备信息 ==\n");
        info.append("App：").append(appVersionName())
                .append(" · ").append(RuntimeMode.agentLabel(this)).append('\n');
        info.append("机型：").append(Build.MANUFACTURER).append(' ').append(Build.BRAND)
                .append(' ').append(Build.MODEL)
                .append(" [").append(Build.DEVICE).append("]\n");
        info.append("系统：Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        info.append("补丁：").append(Build.VERSION.SECURITY_PATCH).append('\n');
        info.append("构建：").append(Build.ID).append('\n');
        info.append("架构：").append(TextUtils.join(", ", Build.SUPPORTED_ABIS)).append('\n');
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        info.append("屏幕：").append(metrics.widthPixels).append('x').append(metrics.heightPixels)
                .append(" @").append(metrics.densityDpi).append("dpi\n");
        appendWifiCapability(info);
        appendBluetoothCapability(info);
        info.append("电话角色：").append(dialerRoleHeld() ? "已启用" : "未启用").append('\n');
        info.append("Shizuku：").append(shizukuReady() ? "已授权" : "未授权").append('\n');
        return info.toString();
    }

    /** Wi-Fi 能力与当前连接（2.4/5/6GHz、协商速率、Wi-Fi 代际）。逐项容错，读不到就降级说明。 */
    private void appendWifiCapability(StringBuilder info) {
        info.append("Wi-Fi：");
        try {
            WifiManager wifi = getSystemService(WifiManager.class);
            if (wifi == null) {
                info.append("不可读（无 Wi-Fi 服务）\n");
                return;
            }
            StringBuilder caps = new StringBuilder(wifi.isWifiEnabled() ? "已开启" : "已关闭");
            try {
                if (wifi.is5GHzBandSupported()) caps.append(" · 支持 5GHz");
            } catch (Exception ignored) { }
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    if (wifi.is6GHzBandSupported()) caps.append(" · 支持 6GHz");
                } catch (Exception ignored) { }
            }
            info.append(caps).append('\n');
            try {
                WifiInfo connection = wifi.getConnectionInfo();
                if (connection != null) {
                    int frequency = connection.getFrequency();
                    if (frequency >= 2400) {
                        // 本构建的 android.jar 为精简 stub，不含 API 31+ 的
                        // getWifiStandard()/getLinkSpeedMbps()；getLinkSpeed() 已弃用但可用。
                        info.append("Wi-Fi 连接：").append(describeWifiBand(frequency))
                                .append(" · ").append(connection.getLinkSpeed()).append("Mbps\n");
                    } else {
                        info.append("Wi-Fi 连接：未连接\n");
                    }
                }
            } catch (Exception error) {
                info.append("Wi-Fi 连接：不可读（").append(error.getClass().getSimpleName()).append("）\n");
            }
        } catch (Exception error) {
            info.append("不可读（").append(error.getClass().getSimpleName()).append("）\n");
        }
    }

    private String describeWifiBand(int frequencyMHz) {
        if (frequencyMHz >= 5955 && frequencyMHz < 7115) return "6GHz (Wi-Fi 6E)";
        if (frequencyMHz >= 4900 && frequencyMHz < 5950) return "5GHz";
        if (frequencyMHz >= 2400 && frequencyMHz < 2500) return "2.4GHz";
        return frequencyMHz + "MHz";
    }

    /** 蓝牙状态与 LE 能力（2M PHY / Coded PHY / 扩展广播均属 BT5 特性）。 */
    private void appendBluetoothCapability(StringBuilder info) {
        info.append("蓝牙：");
        try {
            BluetoothManager manager = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                info.append("本机无蓝牙\n");
                return;
            }
            StringBuilder caps = new StringBuilder(adapter.isEnabled() ? "已开启" : "已关闭");
            caps.append(" · BLE ")
                    .append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                            ? "支持" : "不支持");
            boolean connectGranted = Build.VERSION.SDK_INT < 31
                    || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
            if (connectGranted) {
                try {
                    if (adapter.isLe2MPhySupported()) caps.append(" · LE 2M PHY (BT5+)");
                } catch (Exception ignored) { }
                try {
                    if (adapter.isLeCodedPhySupported()) caps.append(" · LE Coded PHY (远距)");
                } catch (Exception ignored) { }
                try {
                    if (adapter.isLeExtendedAdvertisingSupported()) caps.append(" · LE 扩展广播");
                } catch (Exception ignored) { }
            } else {
                caps.append(" · 能力详情需授权 BLUETOOTH_CONNECT");
            }
            info.append(caps).append('\n');
        } catch (Exception error) {
            info.append("不可读（").append(error.getClass().getSimpleName()).append("）\n");
        }
    }

    private boolean hasStoredPairingCredentials() {
        if (VoWLANPairingStore.configured(this)) return true;
        if (RuntimeMode.isStandalone(this)) {
            return new StandaloneAgentStore(this).configured();
        }
        return false;
    }

    private void clearPairingCredentials() {
        VoWLANPairingStore.clear(this);
        if (RuntimeMode.isStandalone(this)) {
            StandaloneAgentGateway.get(this).clearPairing();
            StandaloneAgentService.resetPairing(this);
        }
        CallRepository.disconnectAll();
        refreshAgent(true);
        toast("已解除配对并清除凭据");
        if (currentPage == Page.SETTINGS) {
            showPage(Page.SETTINGS);
        } else if (currentPage == Page.STATUS) {
            showPage(Page.STATUS);
        }
    }

    private void openPairingPage() {
        handler.removeCallbacks(debugRefresh);
        voBLEAuthenticationVisible = false;
        handler.removeCallbacks(voBLEAuthenticationRefresh);
        bottomBar.setVisibility(View.GONE);
        presentSubPage("AirChat 配对");
        contentHost.removeAllViews();
        contentHost.addView(pairingPage(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        startPairing();
    }

    private void openVoBLEAuthenticationPage() {
        // 防御：Lite 变体没有 VoBLE 服务声明，入口不应从任何路径打开该页面。
        if (!FeatureFlags.voBLEAvailable(this)) return;
        handler.removeCallbacks(debugRefresh);
        handler.removeCallbacks(voBLEAuthenticationRefresh);
        voBLEAuthenticationVisible = true;
        bottomBar.setVisibility(View.GONE);
        presentSubPage("VoBLE 蓝牙配对");
        contentHost.removeAllViews();
        contentHost.addView(voBLEAuthenticationPage(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        handler.post(voBLEAuthenticationRefresh);
    }

    private View voBLEAuthenticationPage() {
        LinearLayout root = pageRoot();
        TextView back = label("‹", 40, TEXT, Gravity.START);
        back.setContentDescription("返回设置");
        back.setOnClickListener(ignored -> {
            bottomBar.setVisibility(View.VISIBLE);
            showPage(Page.SETTINGS);
        });
        root.addView(back, new LinearLayout.LayoutParams(dp(56), dp(52)));

        TextView title = label("VoBLE 蓝牙配对", 30, TEXT, Gravity.START);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, matchWithTop(8));
        root.addView(description("蓝牙是独立连接模式；认证凭据不会影响 VoWLAN 或云端 Relay。"),
                matchWithTop(6));

        boolean permissions = hasVoBLEPermissions();
        boolean paired = com.airsim.phonecontrol.voble.VoBLEGatewayService
                .hasStoredCredential(this);
        LinearLayout statusCard = card(SURFACE, 22);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        statusCard.addView(overline("认证状态"));
        voBLEAuthenticationStatusView = label(
                MainScreenPresentation.voBLEAuthenticationStatus(
                        paired,
                        com.airsim.phonecontrol.voble.VoBLEGatewayService.isAuthenticated(),
                        com.airsim.phonecontrol.voble.VoBLEGatewayService.pendingPairingCode()),
                19, paired ? PRIMARY : WARNING, Gravity.START);
        statusCard.addView(voBLEAuthenticationStatusView, matchWithTop(7));
        statusCard.addView(label(permissions ? "蓝牙权限：已授权" : "蓝牙权限：需要授权",
                13, permissions ? MUTED : WARNING, Gravity.START), matchWithTop(7));
        voBLEDiagnosticsView = label(
                com.airsim.phonecontrol.voble.VoBLEGatewayService.diagnosticsSummary(),
                13, MUTED, Gravity.START);
        statusCard.addView(voBLEDiagnosticsView, matchWithTop(7));
        statusCard.addView(overline("六位安全码"), matchWithTop(18));
        voBLEPairingCodeView = label(
                MainScreenPresentation.voBLEPairingCode(
                        com.airsim.phonecontrol.voble.VoBLEGatewayService.pendingPairingCode()),
                28, MUTED, Gravity.CENTER);
        voBLEPairingCodeView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        statusCard.addView(voBLEPairingCodeView, matchWithTop(8));
        root.addView(statusCard, matchWithTop(18));

        LinearLayout flowCard = card(SURFACE, 22);
        flowCard.setOrientation(LinearLayout.VERTICAL);
        flowCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        flowCard.addView(overline("认证流程"));
        flowCard.addView(label("1. AirChat 扫描并连接此 Android Agent\n"
                        + "2. 两端核对同一组六位安全码\n"
                        + "3. 确认后保存应用层密钥并开放拨号与 PCM 音频",
                14, TEXT, Gravity.START), matchWithTop(8));
        flowCard.addView(description("如果 AirChat 显示 already paired 或认证失败，表示两端旧密钥不一致。"
                + "请在此页清除旧凭据，再回到 AirChat 重新配对。"), matchWithTop(10));
        root.addView(flowCard, matchWithTop(12));

        TextView restart = secondaryButton("重启 VoBLE 服务");
        restart.setOnClickListener(ignored -> {
            com.airsim.phonecontrol.voble.VoBLEGatewayService.start(this);
            toast("VoBLE 服务已启动");
            openVoBLEAuthenticationPage();
        });
        root.addView(restart, matchWithTop(16));

        voBLEClearCredentialButton = secondaryButton("清除 VoBLE 配对凭据");
        voBLEClearCredentialButton.setTextColor(paired ? DANGER : MUTED);
        voBLEClearCredentialButton.setEnabled(paired);
        voBLEClearCredentialButton.setOnClickListener(ignored -> new AlertDialog.Builder(this)
                .setTitle("清除旧的 VoBLE 配对？")
                .setMessage("仅删除蓝牙认证密钥并结束当前 VoBLE 会话；不会清除 VoWLAN、Relay 或通话记录。")
                .setPositiveButton("清除并重新配对", (dialog, which) -> {
                    com.airsim.phonecontrol.voble.VoBLEGatewayService.clearStoredCredential(this);
                    handler.postDelayed(() -> {
                        toast("VoBLE 旧凭据已清除");
                        openVoBLEAuthenticationPage();
                    }, 150);
                })
                .setNegativeButton("取消", null)
                .show());
        root.addView(voBLEClearCredentialButton, matchWithTop(10));
        return scroll(root);
    }

    private void renderVoBLEAuthenticationStatus() {
        boolean stored = com.airsim.phonecontrol.voble.VoBLEGatewayService
                .hasStoredCredential(this);
        boolean authenticated = com.airsim.phonecontrol.voble.VoBLEGatewayService.isAuthenticated();
        String pairingCode = com.airsim.phonecontrol.voble.VoBLEGatewayService.pendingPairingCode();
        if (voBLEAuthenticationStatusView != null) {
            voBLEAuthenticationStatusView.setText(MainScreenPresentation.voBLEAuthenticationStatus(
                    stored, authenticated, pairingCode));
            voBLEAuthenticationStatusView.setTextColor(
                    authenticated || stored ? PRIMARY : WARNING);
        }
        if (voBLEPairingCodeView != null) {
            String formatted = MainScreenPresentation.formatPairingCode(pairingCode);
            voBLEPairingCodeView.setText(MainScreenPresentation.voBLEPairingCode(pairingCode));
            voBLEPairingCodeView.setTextColor("--- ---".equals(formatted) ? MUTED : PRIMARY);
        }
        if (voBLEDiagnosticsView != null) {
            voBLEDiagnosticsView.setText(
                    com.airsim.phonecontrol.voble.VoBLEGatewayService.diagnosticsSummary());
        }
        if (voBLEClearCredentialButton != null) {
            voBLEClearCredentialButton.setEnabled(stored);
            voBLEClearCredentialButton.setTextColor(stored ? DANGER : MUTED);
        }
    }

    private View pairingPage() {
        LinearLayout root = pageRoot();
        TextView back = label("‹", 40, TEXT, Gravity.START);
        back.setContentDescription("返回");
        back.setOnClickListener(ignored -> {
            stopPairingServer();
            showPage(Page.STATUS);
        });
        root.addView(back, new LinearLayout.LayoutParams(dp(56), dp(52)));

        TextView title = label("一次性配对", 30, TEXT, Gravity.START);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, matchWithTop(8));
        root.addView(description("让 macOS 端、iOS 端和安卓端的 AirChat 客户端与本设备连接同一局域网、Wi‑Fi 或移动热点"), matchWithTop(6));

        LinearLayout pairingCard = card(SURFACE, 28);
        pairingCard.setOrientation(LinearLayout.VERTICAL);
        pairingCard.setGravity(Gravity.CENTER_HORIZONTAL);
        pairingCard.setPadding(dp(20), dp(24), dp(20), dp(24));

        pairingDeviceInfoView = label("可连接设备: " + AndroidAgentIdentity.deviceName(), 14, CYAN, Gravity.CENTER);
        pairingDeviceInfoView.setTypeface(Typeface.DEFAULT_BOLD);
        pairingDeviceInfoView.setBackground(rounded(SURFACE_HIGH, 10, BORDER, 1));
        pairingDeviceInfoView.setPadding(dp(14), dp(8), dp(14), dp(8));
        pairingCard.addView(pairingDeviceInfoView);

        int controlPort = VoWLANGatewayService.activeControlPort();
        int pcmPort = VoWLANGatewayService.activePcmPort();
        pairingPortsView = label("直连控制端口: " + controlPort + " · 通话音频: " + pcmPort, 12, MUTED, Gravity.CENTER);
        pairingPortsView.setTypeface(Typeface.MONOSPACE);
        pairingCard.addView(pairingPortsView, matchWithTop(8));

        pairingCard.addView(label("配对验证码（一次性）", 14, MUTED, Gravity.CENTER), matchWithTop(16));
        pairingCodeView = label(
                MainScreenPresentation.formatPairingCode(pairingCode), 42, TEXT, Gravity.CENTER);
        pairingCodeView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        pairingCodeView.setLetterSpacing(0.05f);
        pairingCodeView.setBackground(rounded(SURFACE_HIGH, 18, BORDER, 1));
        pairingCodeView.setPadding(dp(18), dp(18), dp(18), dp(18));
        pairingCard.addView(pairingCodeView, matchWithTop(12));

        pairingCountdownView = label("正在生成安全密钥…", 14, PRIMARY, Gravity.CENTER);
        pairingCard.addView(pairingCountdownView, matchWithTop(14));

        ImageView link = icon(android.R.drawable.ic_menu_share, CYAN, 80);
        link.setPadding(dp(18), dp(18), dp(18), dp(18));
        pairingCard.addView(link, new LinearLayout.LayoutParams(dp(96), dp(96)));

        pairingStatus = label("正在检查局域网并生成临时密钥…", 14, TEXT, Gravity.CENTER);
        pairingStatus.setMaxLines(6);
        pairingCard.addView(pairingStatus, matchWithTop(10));
        root.addView(pairingCard, matchWithTop(20));

        TextView cancel = secondaryButton("取消配对");
        cancel.setTextColor(DANGER);
        cancel.setOnClickListener(ignored -> {
            stopPairingServer();
            showPage(Page.STATUS);
        });
        pairingActionButton = cancel;
        root.addView(cancel, matchWithTop(18));

        TextView note = label("临时密钥 · 仅可使用一次", 13, MUTED, Gravity.CENTER);
        note.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_lock_idle_lock, 0, 0, 0);
        note.setCompoundDrawableTintList(ColorStateList.valueOf(MUTED));
        note.setCompoundDrawablePadding(dp(8));
        root.addView(note, matchWithTop(20));
        return scroll(root);
    }

    private LinearLayout debugFilters() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = row();
        String[] filters = {"全部", "Agent", "VoWLAN", "PCM", "Telecom"};
        for (String filter : filters) {
            boolean selected = filter.equals(activeDebugFilter);
            TextView chip = label(filter, 13, selected ? BG : TEXT, Gravity.CENTER);
            chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setPadding(dp(16), dp(9), dp(16), dp(9));
            chip.setBackground(rounded(selected ? PRIMARY : SURFACE_HIGH, 18, selected ? PRIMARY : BORDER, 1));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.rightMargin = dp(8);
            filterRow.addView(chip, chipParams);
            chip.setOnClickListener(ignored -> {
                activeDebugFilter = filter;
                showPage(Page.DEBUG);
            });
        }
        scroll.addView(filterRow);
        LinearLayout holder = column(0);
        holder.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private View modeSelector() {
        selectedMode = selectedMode == null ? AppConfig.mode(this) : selectedMode;
        LinearLayout segment = row();
        segment.setPadding(dp(3), dp(3), dp(3), dp(3));
        segment.setBackground(rounded(SURFACE_HIGH, 24, BORDER, 1));
        segment.addView(segmentItem("远端静默", AppConfig.MODE_REMOTE_SILENT), weighted());
        segment.addView(segmentItem("本机显示", AppConfig.MODE_LOCAL_AND_PUSH), weighted());
        return segment;
    }

    private TextView segmentItem(String title, String mode) {
        boolean selected = mode.equals(selectedMode);
        TextView item = label(title, 14, selected ? PRIMARY : MUTED, Gravity.CENTER);
        item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        item.setPadding(dp(8), dp(12), dp(8), dp(12));
        item.setBackground(rounded(selected ? tintContainer : Color.TRANSPARENT,
                20, selected ? PRIMARY : Color.TRANSPARENT, 1));
        item.setOnClickListener(ignored -> {
            selectedMode = mode;
            if (currentPage == Page.STATUS || currentPage == Page.SETTINGS) showPage(currentPage);
        });
        return item;
    }

    private static final class HealthTile {
        final LinearLayout view;
        final ImageView icon;
        final TextView state;

        HealthTile(LinearLayout view, ImageView icon, TextView state) {
            this.view = view;
            this.icon = icon;
            this.state = state;
        }
    }

    private HealthTile healthTile(String title, String value, int iconResource, boolean healthy) {
        LinearLayout tile = card(SURFACE, 18);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), dp(15), dp(12), dp(15));
        ImageView icon = icon(iconResource, healthy ? PRIMARY : WARNING, 42);
        icon.setBackground(rounded(healthy ? tintContainer : warnContainer, 21,
                Color.TRANSPARENT, 0));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout copy = column(dp(10));
        TextView heading = label(title, 13, TEXT, Gravity.START);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heading);
        TextView state = label(value, 13, healthy ? PRIMARY : WARNING, Gravity.START);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setSingleLine(true);
        state.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(state, matchWithTop(4));
        tile.addView(copy, weighted());
        return new HealthTile(tile, icon, state);
    }

    private void renderPCMStatus(boolean running) {
        HealthTile tile = pcmHealthTile;
        if (tile == null || currentPage != Page.STATUS) return;
        int color = running ? PRIMARY : WARNING;
        tile.state.setText(running ? "运行中" : "待启动");
        tile.state.setTextColor(color);
        tile.icon.setImageTintList(ColorStateList.valueOf(color));
        tile.icon.setBackground(rounded(running ? tintContainer : warnContainer,
                21, Color.TRANSPARENT, 0));
    }

    private LinearLayout pageTitle(String title, String badge) {
        LinearLayout block = column(0);
        TextView heading = label(title, 30, TEXT, Gravity.START);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        block.addView(heading);
        if (badge != null) {
            TextView chip = label(badge, 13, PRIMARY, Gravity.CENTER);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(dp(12), dp(5), dp(12), dp(5));
            chip.setBackground(rounded(tintContainer, 16, Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(6);
            block.addView(chip, params);
        }
        return block;
    }

    private void placeSystemCall() {
        String value = number == null ? "" : number.getText().toString().trim();
        if (value.isEmpty()) { toast("请输入号码"); return; }
        if (!dialerRoleHeld()) {
            toast("请先将 AirSIM 设为默认电话应用");
            requestDialerRole();
            return;
        }
        try {
            Bundle dialExtras = null;
            if (selectedDialSlot >= 0) {
                SIMSlotInfo slot = SIMSlotManager.getSlotByIndex(this, selectedDialSlot);
                if (slot != null && slot.accountHandle() != null) {
                    dialExtras = new Bundle();
                    dialExtras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, slot.accountHandle());
                }
            }
            if (dialExtras == null) {
                dialExtras = DefaultCallAccount.dialExtras(this);
            }
            getSystemService(TelecomManager.class).placeCall(Uri.parse("tel:" + value), dialExtras);
        } catch (RuntimeException error) {
            toast(error.getMessage() == null ? "系统拨号失败" : error.getMessage());
        }
    }

    private void chooseDefaultCallAccount() {
        DefaultCallAccount.Snapshot snapshot = DefaultCallAccount.snapshot(this);
        String[] choices = new String[snapshot.accounts().size() + 1];
        choices[0] = "跟随系统默认";
        String key = DefaultCallAccount.selectedKey(this);
        int checked = key.isEmpty() ? 0 : -1;
        for (int i = 0; i < snapshot.accounts().size(); i++) {
            DefaultCallAccount.Account account = snapshot.accounts().get(i);
            choices[i + 1] = account.label() + (account.number().isEmpty() ? "" : "\n" + account.number());
            if (key.equals(account.key())) checked = i + 1;
        }
        new AlertDialog.Builder(this).setTitle("默认通话卡")
                .setSingleChoiceItems(choices, checked, (dialog, which) -> {
                    if (which == 0) DefaultCallAccount.followSystem(this);
                    else DefaultCallAccount.select(this, snapshot.accounts().get(which - 1));
                    dialog.dismiss();
                    showPage(Page.SETTINGS);
                    toast("已保存默认通话卡");
                }).setNegativeButton("取消", null).show();
    }

    private void continueStartupSetup() {
        if (isFinishing() || isDestroyed() || startupSetupInFlight) return;
        startupSetupPending = true;
        if (!resumed) return;
        ShizukuBridgeManager shizuku = ShizukuBridgeManager.get(this);
        ShizukuBridgeManager.StartupSnapshot snapshot = shizuku.startupSnapshot();
        ShizukuStartupPolicy.Prompt prompt = shizukuStartupPolicy.inspect(
                snapshot.installed(), snapshot.running(), snapshot.authorized(), true);
        startupSetupInFlight = true;
        startupSetupPending = false;
        if (prompt != ShizukuStartupPolicy.Prompt.NONE) {
            showShizukuStartupPrompt(prompt);
            return;
        }
        switch (DialerRoleSetupPolicy.initial(
                dialerRoleHeld(), shizuku.dialerRoleAutomationAuthorized())) {
            case COMPLETE -> checkAVFEnvironmentAtStartup();
            case SHIZUKU -> attemptPrivilegedDialerRole(false);
            case SYSTEM_ROLE -> requestDialerRole();
            case SETTINGS -> openDefaultAppsSettings();
        }
    }

    private void scheduleStartupSetup() {
        handler.removeCallbacks(startupSetup);
        handler.postDelayed(startupSetup, 500);
    }

    private void showShizukuStartupPrompt(ShizukuStartupPolicy.Prompt prompt) {
        String title;
        String message;
        String action;
        switch (prompt) {
            case INSTALL -> {
                title = "需要安装 Shizuku";
                message = "AirSIM 的通话音频桥需要 Shizuku。当前未检测到安装；安装后请启动服务并授权 AirSIM。"
                        + "\n\n官方下载：\n" + ShizukuBridgeManager.DOWNLOAD_URL;
                action = "下载 Shizuku";
            }
            case START -> {
                title = "Shizuku 尚未运行";
                message = "已安装 Shizuku，但服务尚未启动。请打开 Shizuku，使用无线调试或 Root 启动服务，再返回 AirSIM。"
                        + "\n\n未就绪时，通话音频无法转送到 AirChat。";
                action = "打开 Shizuku";
            }
            case AUTHORIZE -> {
                title = "需要 Shizuku 授权";
                message = "Shizuku 已运行，但 AirSIM 尚未获授权。请允许 AirSIM 使用 Shizuku，以启动通话音频桥。"
                        + "\n\n若此前拒绝过，请在 Shizuku 的「已授权应用」中允许 AirSIM。";
                action = "授权";
            }
            default -> throw new IllegalArgumentException("No Shizuku prompt to present");
        }
        boolean[] awaitingAction = {false};
        shizukuStartupDialog = new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(action, (dialog, which) -> {
                    if (prompt == ShizukuStartupPolicy.Prompt.AUTHORIZE) {
                        awaitingAction[0] = true;
                        attemptPrivilegedDialerRole(true);
                    } else {
                        awaitingAction[0] = openShizukuStartupAction(prompt);
                    }
                })
                .setNegativeButton("稍后", null).create();
        shizukuStartupDialog.setOnDismissListener(dialog -> {
            shizukuStartupDialog = null;
            if (!awaitingAction[0] && !isFinishing() && !isDestroyed()) {
                startupSetupInFlight = false;
                startupSetupPending = true;
                scheduleStartupSetup();
            }
        });
        shizukuStartupDialog.show();
        if (prompt == ShizukuStartupPolicy.Prompt.INSTALL) {
            TextView text = shizukuStartupDialog.findViewById(android.R.id.message);
            if (text != null) {
                // Selectable URL remains usable even if no browser is installed.
                text.setTextIsSelectable(true);
            }
        }
    }

    private boolean openShizukuStartupAction(ShizukuStartupPolicy.Prompt prompt) {
        try {
            Intent intent = prompt == ShizukuStartupPolicy.Prompt.INSTALL
                    ? new Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuBridgeManager.DOWNLOAD_URL))
                    : getPackageManager().getLaunchIntentForPackage(ShizukuBridgeManager.MANAGER_PACKAGE);
            if (intent == null) {
                toast("无法打开 Shizuku，请从官方下载页安装");
                return false;
            }
            // Keep the return callback in this Activity's task, even if the
            // package manager's launch intent originally had NEW_TASK set.
            intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityForResult(intent, SHIZUKU_SETUP_REQUEST);
            return true;
        } catch (RuntimeException error) {
            BridgeLog.error("shizuku_startup_action_failed", error);
            toast("无法打开页面，请复制弹窗中的官方下载地址");
            return false;
        }
    }

    private void completeStartupSetup() {
        startupSetupInFlight = false;
        startupSetupPending = false;
        startupSetupCompleted = true;
        handler.post(this::maybeShowCrashReportPrompt);
    }

    private void requestDialerRole() {
        RoleManager manager = getSystemService(RoleManager.class);
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            toast("设备不支持默认拨号器角色");
            continueAfterSystemRoleRequest();
            return;
        }
        try {
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER), ROLE_REQUEST);
        } catch (RuntimeException error) {
            BridgeLog.error("dialer_role_system_request_failed", error);
            continueAfterSystemRoleRequest();
        }
    }

    private void continueAfterSystemRoleRequest() {
        ShizukuBridgeManager shizuku = ShizukuBridgeManager.get(this);
        switch (DialerRoleSetupPolicy.afterSystemRequest(
                dialerRoleHeld(), shizuku.dialerRoleAutomationAvailable())) {
            case COMPLETE -> checkAVFEnvironmentAtStartup();
            case SHIZUKU -> attemptPrivilegedDialerRole(true);
            case SETTINGS -> openDefaultAppsSettings();
            case SYSTEM_ROLE -> requestDialerRole();
        }
    }

    private void attemptPrivilegedDialerRole(boolean requestPermission) {
        ShizukuBridgeManager.get(this).ensureDialerRole(requestPermission, (success, detail) -> {
            if (isFinishing() || isDestroyed()) return;
            refreshStatus();
            DialerRoleSetupPolicy.Action next = DialerRoleSetupPolicy.afterPrivilegedAttempt(
                    success && dialerRoleHeld());
            if (next == DialerRoleSetupPolicy.Action.COMPLETE) {
                toast("默认电话角色已由系统启用");
                checkAVFEnvironmentAtStartup();
            } else {
                toast(detail + "，请在默认应用中选择 AirSIM");
                openDefaultAppsSettings();
            }
        });
    }

    private void openDefaultAppsSettings() {
        Intent settings = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        try {
            startActivityForResult(settings, DEFAULT_APPS_REQUEST);
        } catch (RuntimeException error) {
            BridgeLog.error("default_apps_settings_open_failed", error);
            Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(details, DEFAULT_APPS_REQUEST);
        }
    }

    private void localCall(String action) {
        if (!dialerRoleHeld()) {
            toast("当前通话属于其他默认电话应用；请先选择 AirSIM");
            requestDialerRole();
            return;
        }
        String callId = CallRepository.idForAction(action);
        if (callId.isEmpty()) { toast("当前没有可控制的通话"); return; }
        Executors.newSingleThreadExecutor().execute(() -> {
            CallRepository.ActionResult result = CallRepository.execute(
                    MainActivity.this, new AgentCommand(UUID.randomUUID().toString(), action, callId, ""));
            runOnUiThread(() -> toast(result.success ? "Telecom 已确认" : result.error));
        });
    }

    private void refreshAgent() {
        refreshAgent(false);
    }

    private void refreshAgent(boolean showResult) {
        final int generation = ++agentRefreshGeneration;
        agentDetail = "检测中";
        if (showResult) setAgentRefreshMessage("正在请求 " + AppConfig.endpoint(this)
                + " 的 Agent 状态…", MUTED);
        updateVisibleStatus();
        Executors.newSingleThreadExecutor().execute(() -> {
            String address = AppConfig.endpoint(this);
            String path = AppConfig.configured(this) ? "/api/android/status" : "/api/health";
            try {
                boolean paired = AppConfig.configured(this);
                AgentClient client = new AgentClient(this);
                String payload = paired ? client.status() : client.health();
                JSONObject result = new JSONObject(payload);
                boolean healthy = result.optBoolean("ok", false);
                String profile = result.optString("runtime_profile", "未知");
                String detail = paired
                        ? "接口：GET " + path + " · HTTP 200\nAgent：" + address
                                + "\n运行模式：" + profile
                                + " · 待执行命令：" + result.optInt("pending_commands", 0)
                                + "\n最近活动：" + result.optString("last_seen", "未记录")
                        : "接口：GET " + path + " · HTTP 200\nAgent：" + address
                                + "\n版本：" + result.optString("version", "未知")
                                + " · 运行模式：" + profile
                                + "\n控制令牌：尚未配置";
                BridgeLog.info("agent_status_refresh path=" + path + " ok=" + healthy
                        + " profile=" + profile + " endpoint=" + address);
                runOnUiThread(() -> {
                    if (generation != agentRefreshGeneration) return;
                    agentOnline = paired && healthy;
                    agentDetail = healthy
                            ? (paired ? "在线" : "可达 · 尚未配对控制令牌")
                            : "可达 · Agent 报告未就绪";
                    if (showResult) setAgentRefreshMessage(
                            (healthy ? "刷新成功" : "Agent 未就绪") + "\n" + detail,
                            healthy ? PRIMARY : WARNING);
                    updateVisibleStatus();
                });
                try {
                    JSONObject pushStatus = new JSONObject(client.pushStatus());
                    runOnUiThread(() -> {
                        if (generation != agentRefreshGeneration) return;
                        updatePushStatus(pushStatus);
                        updateVisibleStatus();
                    });
                } catch (Exception error) {
                    BridgeLog.error("push_status_refresh_failed endpoint=" + address, error);
                    String pushError = error.getClass().getSimpleName() + " · " + error.getMessage();
                    runOnUiThread(() -> {
                        if (generation != agentRefreshGeneration) return;
                        pushConfigured = false;
                        pushDetail = "状态不可读";
                        cloudRelayConfigured = false;
                        cloudLastError = DebugRedactor.sanitize(pushError);
                        pushDiagnostic = "读取 /api/push/status 失败："
                                + DebugRedactor.sanitize(pushError);
                        updateVisibleStatus();
                    });
                }
            } catch (Exception error) {
                BridgeLog.error("agent_status_refresh_failed path=" + path + " endpoint=" + address, error);
                runOnUiThread(() -> {
                    if (generation != agentRefreshGeneration) return;
                    agentOnline = false;
                    agentDetail = "离线 · " + error.getClass().getSimpleName();
                    pushConfigured = false;
                    pushDetail = "Agent 不可达";
                    cloudRelayConfigured = false;
                    cloudLastError = RuntimeMode.agentLabel(this) + "不可达";
                    pushDiagnostic = "先恢复" + RuntimeMode.agentLabel(this)
                            + "；当前无法查询 /api/push/status。";
                    if (showResult) setAgentRefreshMessage("刷新失败 · GET " + path
                            + "\nAgent：" + address + "\n原因："
                            + DebugRedactor.sanitize(error.getClass().getSimpleName() + " · "
                                    + String.valueOf(error.getMessage())), DANGER);
                    updateVisibleStatus();
                });
            }
        });
    }

    private void setAgentRefreshMessage(String message, int color) {
        agentRefreshMessage = message;
        agentRefreshColor = color;
        if (agentRefreshResult != null) {
            agentRefreshResult.setText(message);
            agentRefreshResult.setTextColor(color);
        }
    }

    private void updatePushStatus(JSONObject result) {
        boolean enabled = result.optBoolean("cloud_enabled", false);
        boolean calls = result.optBoolean("call_push_ready", false);
        boolean messages = result.optBoolean("message_push_ready", false);
        String relay = result.optString("relay_url", "");
        String lastError = result.optString("last_error", "");
        cloudEnabled = enabled;
        cloudRelayConfigured = enabled && result.optBoolean("configured", false) && !relay.isEmpty();
        cloudLastError = DebugRedactor.sanitize(lastError);
        pushConfigured = enabled && calls && messages;
        pushDetail = !enabled ? "云端已关闭" : pushConfigured ? "推送已配置" : "配置不完整";
        pushDiagnostic = "云端开关：" + (enabled ? "开启" : "关闭")
                + "\n来电 Push：" + (calls ? "已配置" : "未就绪")
                + " · 短信 Push：" + (messages ? "已配置" : "未就绪")
                + "\nRelay：" + (relay.isEmpty() ? "未配置" : relay)
                + "\nAPNs 环境：" + result.optString("environment", "未知")
                + " · WAN：" + result.optString("wan_interface", "未检测到")
                + (lastError.isEmpty() ? "" : "\n最近错误：" + DebugRedactor.sanitize(lastError))
                + "\n注意：已配置不等于公网 Relay 已验证可达。";
    }

    private void showFeatureDetails(Feature feature) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(featureTitle(feature))
                .setMessage(featureDetails(feature, ""))
                .setPositiveButton("关闭", null)
                .setNeutralButton("重新检查", (ignored, which) -> showFeatureDetails(feature))
                .create();
        dialog.show();
        if (feature != Feature.AGENT && feature != Feature.PUSH) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            String live;
            try {
                AgentClient client = new AgentClient(this);
                JSONObject result = new JSONObject(feature == Feature.PUSH
                        ? client.pushStatus()
                        : AppConfig.configured(this) ? client.status() : client.health());
                if (feature == Feature.PUSH) {
                    String cloudLogs;
                    try {
                        cloudLogs = cloudAgentLogs(new JSONObject(client.debugSnapshot()));
                    } catch (Exception error) {
                        cloudLogs = "Agent 云端事件读取失败：" + DebugRedactor.sanitize(
                                error.getClass().getSimpleName() + " · " + error.getMessage());
                    }
                    String liveCloudLogs = cloudLogs;
                    runOnUiThread(() -> {
                        updatePushStatus(result);
                        updateVisibleStatus();
                        if (dialog.isShowing()) dialog.setMessage(featureDetails(feature,
                                "实时查询：/api/push/status · HTTP 200\n\nAgent 云端事件（新到旧）\n"
                                        + liveCloudLogs));
                    });
                    return;
                }
                live = "实时查询：" + (AppConfig.configured(this) ? "/api/android/status" : "/api/health")
                        + " · HTTP 200\n返回：ok=" + result.optBoolean("ok", false)
                        + " · runtime_profile=" + result.optString("runtime_profile", "未知")
                        + (AppConfig.configured(this)
                            ? " · pending_commands=" + result.optInt("pending_commands", 0)
                            : " · version=" + result.optString("version", "未知"));
            } catch (Exception error) {
                live = "实时查询失败：" + DebugRedactor.sanitize(
                        error.getClass().getSimpleName() + " · " + error.getMessage());
                BridgeLog.error("feature_diagnostic_failed feature=" + feature, error);
            }
            String outcome = live;
            runOnUiThread(() -> {
                if (dialog.isShowing()) dialog.setMessage(featureDetails(feature, outcome));
            });
        });
    }

    private String cloudAgentLogs(JSONObject snapshot) {
        JSONArray events = snapshot.optJSONArray("events");
        if (events == null) return "Agent 未返回事件列表";
        StringBuilder output = new StringBuilder();
        int count = 0;
        for (int index = events.length() - 1; index >= 0 && count < 10; index--) {
            JSONObject event = events.optJSONObject(index);
            if (event == null) continue;
            String category = event.optString("category", "");
            if (!category.equals("push") && !category.startsWith("cloud")) continue;
            if (output.length() > 0) output.append('\n');
            output.append(DebugRedactor.sanitize(event.optString("timestamp", "")))
                    .append(" · ").append(category)
                    .append(" · ").append(DebugRedactor.sanitize(event.optString("summary", "")));
            String payload = event.optString("payload", "");
            if (!payload.isEmpty()) output.append("\n  ").append(
                    payload.contains("://") ? "[可能包含媒体凭据，链接详情已省略]"
                            : DebugRedactor.sanitize(payload));
            count++;
        }
        return output.length() == 0 ? "暂无 Push / Cloud 事件" : output.toString();
    }

    private String featureTitle(Feature feature) {
        return switch (feature) {
            case VOWLAN -> "VoWLAN 连接详情";
            case AGENT -> RuntimeMode.agentLabel(this) + " 详情";
            case PCM -> "PCM 音频桥详情";
            case PUSH -> "Push / Relay 详情";
            case SHIZUKU -> "Shizuku 详情";
        };
    }

    private String featureDetails(Feature feature, String live) {
        String log = BridgeLog.read();
        String latestVoWLAN = MainScreenPresentation.latestContaining(log, "vowlan_state");
        String currentVoWLAN = VoWLANGatewayService.currentDiagnosticState();
        String details;
        String[] keywords;
        switch (feature) {
            case VOWLAN -> {
                boolean paired = VoWLANPairingStore.configured(this);
                String address = localVoWLANAddress();
                String pcmBackend = RuntimeMode.isStandalone(this) ? "本机 PCM 端口 7580" : "AVF PCM 端口 7580";
                String reason = !paired ? "尚未与 AirChat 配对"
                        : address.isEmpty() ? "没有可用的同网 Wi-Fi 或热点地址"
                        : currentVoWLAN.contains("avf=missing") ? "AVF 虚拟网卡未出现"
                        : currentVoWLAN.contains("agent_ready=false") ? "Agent 未通过 VoWLAN 就绪检查"
                        : currentVoWLAN.contains("pcm_ready=false") ? pcmBackend + "未就绪"
                        : !vowlanReady() ? "VoWLAN 服务尚未完成当前检查：" + currentVoWLAN
                        : "当前检查项满足；仍需实际通话验证";
                details = "配对：" + (paired ? "已保存密钥" : "未配对")
                        + "\n本机局域网：" + (address.isEmpty() ? "未发现" : address)
                        + "\n状态原因：" + reason
                        + "\n当前检查：" + currentVoWLAN
                        + "\n最近检查：" + (latestVoWLAN.isEmpty() ? "无" : latestVoWLAN);
                keywords = new String[]{"vowlan", "pairing"};
            }
            case AGENT -> {
                boolean standalone = RuntimeMode.isStandalone(this);
                details = "状态：" + agentDetail
                        + (standalone ? "\n运行位置：本 APK 前台服务"
                                : "\n地址：" + AppConfig.endpoint(this)
                                + "\n控制令牌：" + (AppConfig.configured(this) ? "已配置" : "未配置"))
                        + "\n原因：" + (agentOnline ? "最近一次状态检查通过"
                            : standalone ? "检查前台服务、通知权限与系统省电限制"
                            : "检查 AVF Linux 是否运行、地址是否为 :8575，以及控制令牌是否匹配")
                        + "\n" + agentRefreshMessage;
                keywords = new String[]{"agent", "watchdog", "http_request", "avf"};
            }
            case PCM -> {
                String shizuku = ShizukuBridgeManager.get(this).status();
                String pcmBackend = RuntimeMode.isStandalone(this) ? "本机 PCM 端口 7580" : "AVF PCM 端口 7580";
                String reason = !shizukuReady() ? "Shizuku 未连接或未授权，PCM 用户服务不可用"
                        : currentVoWLAN.contains("pcm_ready=false") ? pcmBackend + "未响应"
                        : !pcmBridgeRunning() ? "PCM 音频桥进程尚未运行"
                        : "当前检查项满足；仍需实际通话验证双向音频";
                details = "Shizuku：" + shizuku + "\n桥状态：" + (pcmBridgeRunning() ? "运行中" : "待启动")
                        + "\n状态原因：" + reason
                        + "\n当前 VoWLAN 检查：" + currentVoWLAN
                        + "\n最近 VoWLAN 检查：" + (latestVoWLAN.isEmpty() ? "无" : latestVoWLAN);
                keywords = new String[]{"pcm", "shizuku", "vowlan_state"};
            }
            case PUSH -> {
                details = "状态：" + pushDetail + "\n" + pushDiagnostic
                        + "\n原因：" + (agentOnline ? "请核对开关、Relay 地址、APNs token 与最近错误"
                            : "Agent 不可达时无法确认云端注册或心跳");
                keywords = new String[]{"push", "relay", "pairing"};
            }
            case SHIZUKU -> {
                String shizuku = ShizukuBridgeManager.get(this).status();
                details = "状态：" + shizuku + "\n原因：" + (shizukuReady()
                        ? "Binder 已连接；是否有声音仍需检查 PCM 路由"
                        : "检查 Shizuku 是否运行、AirSIM 授权及 PCM 用户服务是否成功绑定");
                keywords = new String[]{"shizuku", "local_output"};
            }
            default -> throw new IllegalStateException("未知功能");
        }
        String recent = MainScreenPresentation.recentMatchingLogLines(log, 12, keywords);
        return details + (live.isEmpty() ? "" : "\n\n" + live)
                + "\n\n相关日志（新到旧）\n" + recent;
    }

	private void checkAVFEnvironmentAtStartup() {
		if (RuntimeMode.isStandalone(this)) {
			StandaloneAgentService.start(this);
			completeStartupSetup();
			return;
		}
		if (avfStartupPromptShown) { completeStartupSetup(); return; }
		AVFEnvironmentDetector.Snapshot snapshot = AVFEnvironmentDetector.inspect(this);
		boolean agentConfigured = AppConfig.configured(this);
		if (!AVFStartupPolicy.shouldPrompt(snapshot.state(), agentConfigured)) {
            completeStartupSetup();
            return;
        }
		avfStartupPromptShown = true;
		boolean needsInstall = snapshot.state() == AVFStartupPolicy.State.RUNNING && !agentConfigured;
		boolean canLaunch = snapshot.state() == AVFStartupPolicy.State.READY_TO_START;
		AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle(needsInstall ? "配置 AirSIM AVF Agent" : "需要启动 AVF Linux 环境")
				.setMessage(avfEnvironmentSummary(snapshot)
						+ (needsInstall
						? "\n\n若尚未安装，复制首次安装命令到 Linux Terminal；" +
								"若 Agent 已运行，先执行 sudo airsim-avf-pair 配对并检查 installerd。"
						: "\n\n启动 Linux 后，在 Terminal 中粘贴 AirSIM 一键安装命令。"))
				.setPositiveButton(needsInstall ? "复制首次安装命令"
						: canLaunch ? "启动 Linux Terminal" : "打开开发者选项",
						(ignored, which) -> {
							if (needsInstall) copyAVFInstallCommand();
							else if (canLaunch) openAVFTerminalOrSettings();
							else AVFEnvironmentActions.openDeveloperOptions(this);
						})
				.setNeutralButton(needsInstall ? "打开 Linux Terminal" : "复制安装命令",
						(ignored, which) -> {
							if (needsInstall) openAVFTerminalOrSettings();
							else copyAVFInstallCommand();
						})
				.setNegativeButton("稍后", null)
				.create();
		dialog.setOnDismissListener(ignored -> completeStartupSetup());
		dialog.show();
	}

	private String avfEnvironmentSummary(AVFEnvironmentDetector.Snapshot snapshot) {
		return switch (snapshot.state()) {
			case RUNNING -> "AVF Linux 已启动，可连接 Agent。";
			case READY_TO_START -> "系统已提供 AVF 和 Linux Terminal，但虚拟 Linux 尚未启动。";
			case TERMINAL_DISABLED -> "系统包含 Linux Terminal，但当前被开发者选项停用。";
			case TERMINAL_MISSING -> snapshot.restrictedChinaOEM()
					? "当前国产 ROM 未提供 AOSP Linux Terminal 组件；AirSIM 无法用普通应用权限强制补装或开启。"
					: "系统声明支持 AVF，但未安装 AOSP Linux Terminal 组件。";
			case AVF_UNSUPPORTED -> snapshot.restrictedChinaOEM()
					? "当前国产 ROM 没有向第三方应用公开 AVF Linux 能力，无法由 AirSIM 强制开启。"
					: "当前系统没有公开 AVF 能力，请检查系统版本和厂商支持。";
		};
	}

	private void openAVFTerminalOrSettings() {
		if (!AVFEnvironmentActions.openTerminal(this)) {
			AVFEnvironmentActions.openDeveloperOptions(this);
			toast("未找到可启动的 Linux Terminal，请在开发者选项中启用");
		}
	}

	private void copyAVFInstallCommand() {
		getSystemService(ClipboardManager.class).setPrimaryClip(
				ClipData.newPlainText("AirSIM AVF 一键安装", AVFStartupPolicy.installCommand()));
		toast("AVF 一键安装命令已复制");
	}

	private void refreshInstallerStatus() {
		if (installerStatus == null) return;
		if (!AppConfig.configured(this)) {
			installerStatus.setText("尚未配对控制令牌。先在 AVF Linux Terminal 完成首次签名安装，" +
					"再运行 sudo airsim-avf-pair，将令牌保存到上方配置。" +
					"已有 Agent 但安装服务不可用时，先在 Terminal 检查 airsim-installerd 服务，不要清除 Linux 数据。");
			return;
		}
		Executors.newSingleThreadExecutor().execute(() -> {
			try {
				String value = new AgentReleaseManager(this).status();
				runOnUiThread(() -> {
					if (installerStatus != null) installerStatus.setText("installerd 在线\n" + value);
				});
			} catch (Exception error) {
				runOnUiThread(() -> {
					if (installerStatus != null) installerStatus.setText("installerd 不可用 · " +
							error.getClass().getSimpleName() +
							"。请在 Linux Terminal 检查 sudo systemctl status airsim-installerd；" +
							"不要清除 Linux 数据。");
				});
			}
		});
	}

	private void runAgentMaintenance(String action) {
		if (!AppConfig.configured(this)) {
			if (installerStatus != null) installerStatus.setText(
					"尚未配对。先完成 AVF Linux 首次安装并运行 sudo airsim-avf-pair，" +
					"保存控制令牌后再更新、修复或回滚。");
			return;
		}
		if (installerStatus != null) installerStatus.setText("Agent 操作进行中 · " + action);
		Executors.newSingleThreadExecutor().execute(() -> {
			try {
				AgentReleaseManager manager = new AgentReleaseManager(this);
				String result = switch (action) {
					case "repair" -> manager.repairLatest();
					case "rollback" -> manager.rollback();
					default -> manager.installLatest();
				};
				runOnUiThread(() -> {
					if (installerStatus != null) installerStatus.setText("Agent 操作完成\n" + result);
					toast("AVF Agent 操作完成");
					refreshAgent();
				});
			} catch (Exception error) {
				BridgeLog.error("avf_agent_maintenance_failed action=" + action, error);
				runOnUiThread(() -> {
					if (installerStatus != null) installerStatus.setText("Agent 操作失败 · " + error.getMessage());
					toast("AVF Agent 操作失败");
				});
			}
		});
	}

    private void updateVisibleStatus() {
        if (status != null) status.setText(roleText() + "\nAgent：" + agentDetail);
        if (currentPage == Page.STATUS && contentHost != null
                && bottomBar.getVisibility() == View.VISIBLE) showPage(Page.STATUS);
    }

    private void startPairing() {
        stopPairingServer();
        if (pairingStatus != null) pairingStatus.setText("正在检查局域网并生成临时密钥…");
        pairingServer = new PairingServer(this, new PairingServer.Listener() {
            @Override public void onStarted(String code, String address, String advertisedName,
                                            int pairingPort, int controlPort, int pcmPort, long expiresAtMillis) {
                BridgeLog.info("pairing_started address=" + address + " advertised=" + advertisedName);
                pairingCode = code;
                pairingExpiresAt = expiresAtMillis;
                runOnUiThread(() -> {
                    if (pairingCodeView != null) pairingCodeView.setText(
                            MainScreenPresentation.formatPairingCode(code));
                    if (pairingDeviceInfoView != null) {
                        pairingDeviceInfoView.setText("可连接设备: " + AndroidAgentIdentity.deviceName()
                                + "\n广播标识: " + advertisedName);
                    }
                    if (pairingPortsView != null) {
                        pairingPortsView.setText("配对端口: " + pairingPort + " · 控制面: " + controlPort + " · PCM音频: " + pcmPort);
                    }
                    if (pairingStatus != null) {
                        // 地址行可长按选择复制，便于手动 IPv4+端口 配对（上游 v0.9.7.2）。
                        pairingStatus.setText("等待 macOS 2.0 / iOS 客户端连接…\n" + address
                                + "\n控制端口: " + controlPort + " · PCM音频: " + pcmPort);
                        pairingStatus.setTextIsSelectable(true);
                    }
                    startPairingCountdown();
                });
            }

            @Override public void onSucceeded(String clientDeviceName, String agentResponse) {
                BridgeLog.info("pairing_completed client=" + clientDeviceName);
                boolean localMode = false;
                try {
                    localMode = new JSONObject(agentResponse).optBoolean("local_mode", false);
                } catch (Exception ignored) {
                    // Older Agent responses do not carry a mode marker.
                }
                boolean pairedLocally = localMode;
                int controlPort = VoWLANGatewayService.activeControlPort();
                int pcmPort = VoWLANGatewayService.activePcmPort();
                runOnUiThread(() -> {
                    cancelPairingCountdown();
                    if (pairingCodeView != null) {
                        pairingCodeView.setText("配对成功");
                        pairingCodeView.setTextSize(28);
                    }
                    if (pairingCountdownView != null) pairingCountdownView.setText("VoWLAN 密钥已加密保存");
                    if (pairingDeviceInfoView != null) {
                        pairingDeviceInfoView.setText("已接入客户端: " + clientDeviceName);
                    }
                    if (pairingStatus != null) {
                        pairingStatus.setText("已成功配对接入设备：" + clientDeviceName
                                + "\n支持协议: LD 2.0 / 1.0 双兼容"
                                + "\n控制端口: " + controlPort + " · 音频端口: " + pcmPort
                                + (pairedLocally ? "\n(本地直连模式，免 APNs / Relay)" : "\n(已配置云端 Push)"));
                    }
                    if (pairingActionButton != null) {
                        pairingActionButton.setText("完成并返回");
                        pairingActionButton.setTextColor(CYAN);
                    }
                    refreshAgent();
                });
            }

            @Override public void onFailed(String message) {
                BridgeLog.error("pairing_failed", new IllegalStateException(message));
                runOnUiThread(() -> {
                    cancelPairingCountdown();
                    if (pairingCountdownView != null) pairingCountdownView.setText("配对窗口已关闭");
                    if (pairingStatus != null) pairingStatus.setText("配对失败：" + message);
                });
            }
        });
        pairingServer.start();
    }

    private void startPairingCountdown() {
        cancelPairingCountdown();
        long remaining = Math.max(0, pairingExpiresAt - System.currentTimeMillis());
        pairingCountdown = new CountDownTimer(remaining, 1_000) {
            @Override public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1_000;
                if (pairingCountdownView != null) pairingCountdownView.setText(
                        String.format(java.util.Locale.CHINA, "%02d:%02d 后失效", seconds / 60, seconds % 60));
            }

            @Override public void onFinish() {
                if (pairingCountdownView != null) pairingCountdownView.setText("配对窗口已失效");
            }
        }.start();
    }

    private void cancelPairingCountdown() {
        if (pairingCountdown != null) pairingCountdown.cancel();
        pairingCountdown = null;
    }

    private void stopPairingServer() {
        cancelPairingCountdown();
        PairingServer active = pairingServer;
        pairingServer = null;
        if (active != null) {
            try { active.close(); }
            catch (Exception error) { BridgeLog.error("pairing_close", error); }
        }
    }

    private void refreshStatus() {
        refreshShizukuStatus();
    }

    private void refreshShizukuStatus() {
        if (shizukuStatus != null) shizukuStatus.setText(ShizukuBridgeManager.get(this).status());
    }

    private void refreshDebugLog() {
        if (debugRows == null) return;
        String log = BridgeLog.read();
        if (log.equals(lastRenderedDebugLog) && activeDebugFilter.equals(lastRenderedDebugFilter)) return;
        lastRenderedDebugLog = log;
        lastRenderedDebugFilter = activeDebugFilter;
        debugRows.removeAllViews();
        String visible = MainScreenPresentation.recentLogLines(
                log, activeDebugFilter, 60);
        if (visible.startsWith("暂无") || visible.startsWith("此分类")) {
            TextView empty = description(visible);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(28), dp(8), dp(28));
            debugRows.addView(empty);
            return;
        }
        String[] lines = visible.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(dp(2), dp(6), dp(2), dp(6));

            View dot = new View(this);
            dot.setBackground(rounded(MainScreenPresentation.isErrorLog(lines[index]) ? DANGER : PRIMARY,
                    4, Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
            dotParams.topMargin = dp(5);
            row.addView(dot, dotParams);

            LinearLayout copy = column(dp(9));
            TextView metadata = label(MainScreenPresentation.logMetadata(lines[index]),
                    10, MUTED, Gravity.START);
            copy.addView(metadata);
            TextView title = label(MainScreenPresentation.activitySummary(lines[index]),
                    13, TEXT, Gravity.START);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            copy.addView(title, matchWithTop(1));
            TextView detail = label(MainScreenPresentation.eventBody(lines[index]),
                    11, MUTED, Gravity.START);
            detail.setTypeface(Typeface.MONOSPACE);
            copy.addView(detail, matchWithTop(1));
            row.addView(copy, weighted());
            debugRows.addView(row);

            if (index < lines.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(BORDER);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.leftMargin = dp(18);
                debugRows.addView(divider, dividerParams);
            }
        }
    }

    private void copyDebugLog() {
        String value = BridgeLog.read();
        getSystemService(ClipboardManager.class).setPrimaryClip(
                ClipData.newPlainText("AirSIM Debug Log", value));
        toast("日志已复制");
    }

    private void exportDebugLog() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "AirSIM Android 诊断");
        share.putExtra(Intent.EXTRA_TEXT, BridgeLog.read());
        startActivity(Intent.createChooser(share, "导出诊断"));
    }

	private void shareCrashReport() {
		String payload = CrashReportSharePayload.compose(
				CrashReportStore.read(getFilesDir()), BridgeLog.read());
		if (payload.isBlank()) {
			toast("暂无闪退报告");
			return;
		}
		Intent share = new Intent(Intent.ACTION_SEND);
		share.setType("text/plain");
		share.putExtra(Intent.EXTRA_SUBJECT, "AirSIM Android 闪退报告");
		share.putExtra(Intent.EXTRA_TEXT, payload);
		startActivity(Intent.createChooser(share, "发送闪退报告"));
	}

	private void maybeShowCrashReportPrompt() {
		if (crashPromptShown || !resumed || startupSetupInFlight || isFinishing() || isDestroyed()
                || !CrashReportStore.hasReport(getFilesDir())) return;
		crashPromptShown = true;
		new AlertDialog.Builder(this)
				.setTitle("检测到上次闪退")
				.setMessage("AirSIM 已在本机保存脱敏报告。不会自动上传，是否现在发送给开发者？")
				.setPositiveButton("发送", (ignored, which) -> shareCrashReport())
				.setNeutralButton("查看", (ignored, which) -> showPage(Page.DEBUG))
				.setNegativeButton("稍后", null)
				.show();
	}

    private void saveConfiguration() {
        try {
            if (RuntimeMode.isStandalone(this)) {
                AppConfig.save(this, "", "", selectedMode);
            } else {
                AppConfig.save(this, endpoint.getText().toString(), token.getText().toString(), selectedMode);
            }
            AgentWatchdogService.start(this);
            toast(RuntimeMode.isStandalone(this)
                    ? "模式已保存，内置 Agent 正在启动" : "配置已保存，守护服务正在启动");
            refreshAgent();
        } catch (IllegalArgumentException error) {
            toast(error.getMessage());
        }
    }

    private String roleText() {
        return "默认电话角色：" + (dialerRoleHeld() ? "已启用" : "未启用");
    }

    private boolean dialerRoleHeld() {
        RoleManager manager = getSystemService(RoleManager.class);
        return manager != null && manager.isRoleAvailable(RoleManager.ROLE_DIALER)
                && manager.isRoleHeld(RoleManager.ROLE_DIALER);
    }

    private String vowlanNetworkLine() {
        String host = localVoWLANAddress();
        return host.isEmpty() ? "同一 Wi‑Fi / 热点" : "同一 Wi‑Fi · " + host;
    }

    private boolean vowlanReady() {
        String state = VoWLANGatewayService.currentDiagnosticState();
        return dialerRoleHeld() && VoWLANPairingStore.configured(this) && !localVoWLANAddress().isEmpty()
                && state.contains("paired=true") && state.contains("agent_ready=true")
                && state.contains("pcm_ready=true") && state.contains("dialer_role=true")
                && !state.contains("avf=missing");
    }

    private String localVoWLANAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                if (!item.isUp() || item.isLoopback()) continue;
                Enumeration<InetAddress> addresses = item.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address
                            && VoWLANNetworkPolicy.isVoWLANInterface(item.getName(), address.getHostAddress())) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private boolean shizukuReady() {
        String value = ShizukuBridgeManager.get(this).status();
        return value.contains("Shizuku 已连接");
    }

    private boolean pcmBridgeRunning() {
        return ShizukuBridgeManager.get(this).bridgeRunning();
    }

    private String latestActivity() {
        String line = MainScreenPresentation.recentLogLines(BridgeLog.read(), "", 1);
        return "暂无 Debug 日志".equals(line)
                ? "等待连接活动"
                : "刚刚 · " + MainScreenPresentation.activitySummary(line);
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(30));
        root.setBackgroundColor(BG);
        return root;
    }

    private ScrollView scroll(View content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout card = new LinearLayout(this);
        card.setBackground(rounded(color, radius, BORDER, 1));
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout column(int startPadding) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        if (startPadding > 0) column.setPadding(startPadding, 0, 0, 0);
        return column;
    }

    private TextView sectionTitle(String value) {
        TextView title = label(value, 18, TEXT, Gravity.START);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        return title;
    }

    /** iOS 风格设置分组行：标题 + 副标题，整行可点，带涟漪反馈（LDev 配对/外观分组用）。 */
    private TextView settingsNavRow(String title, String subtitle, int titleColor) {
        TextView rowView = new TextView(this);
        rowView.setText(title + "\n" + subtitle);
        rowView.setTextSize(15);
        rowView.setTextColor(titleColor);
        rowView.setPadding(dp(16), dp(14), dp(16), dp(14));
        rowView.setLineSpacing(dp(3), 1f);
        rowView.setClickable(true);
        GradientDrawable mask = rounded(SURFACE, 12, Color.TRANSPARENT, 0);
        rowView.setBackground(new RippleDrawable(ColorStateList.valueOf(navRipple), null, mask));
        return rowView;
    }

    private TextView overline(String value) {
        TextView text = label(value, 12, MUTED, Gravity.START);
        text.setLetterSpacing(0.04f);
        return text;
    }

    private TextView description(String value) {
        TextView text = label(value, 13, MUTED, Gravity.START);
        text.setLineSpacing(dp(2), 1f);
        return text;
    }

    private TextView label(String value, int sp, int color, int gravity) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(gravity);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private EditText input(String hint, int type) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setHintTextColor(MUTED);
        view.setTextColor(TEXT);
        view.setTextSize(15);
        view.setInputType(type);
        view.setSingleLine(true);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(rounded(SURFACE_HIGH, 15, BORDER, 1));
        return view;
    }

    private TextView primaryButton(String title, int color) {
        TextView button = label(title, 16, Color.WHITE, Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(54));
        button.setPadding(dp(16), dp(13), dp(16), dp(13));
        button.setBackground(rounded(color, 20, color, 1));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView secondaryButton(String title) {
        TextView button = label(title, 14, TEXT, Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(50));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(rounded(SURFACE_HIGH, 18, BORDER, 1));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    /** 红底白字危险操作按钮（LDev）：清除凭据等破坏性操作统一使用。 */
    private TextView dangerButton(String title) {
        TextView button = label(title, 14, Color.WHITE, Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(50));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(rounded(DANGER, 18, DANGER, 1));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private ImageView icon(int resource, int tint, int contentSize) {
        ImageView view = new ImageView(this);
        view.setImageResource(resource);
        view.setImageTintList(ColorStateList.valueOf(tint));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setMinimumWidth(dp(contentSize));
        view.setMinimumHeight(dp(contentSize));
        return view;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0 && strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams weightedWithStart(int start) {
        LinearLayout.LayoutParams params = weighted();
        params.leftMargin = dp(start);
        return params;
    }

    private LinearLayout.LayoutParams weightedWithEnd(int end) {
        LinearLayout.LayoutParams params = weighted();
        params.rightMargin = dp(end);
        return params;
    }

    private LinearLayout.LayoutParams matchWithTop(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == DIAGNOSTIC_TXT_REQUEST) {
            if (result == RESULT_OK && data != null && data.getData() != null) {
                Uri destination = data.getData();
                DIAGNOSTIC_WORKER.execute(() -> {
                    try (java.io.OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                        if (output == null) throw new java.io.IOException("document unavailable");
                        String report = AudioWatchdogReport.compose(AppInformation.version(this), Build.VERSION.SDK_INT,
                                Build.MANUFACTURER, Build.MODEL, BridgeLog.read());
                        output.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        runOnUiThread(() -> toast("Watchdog TXT 已保存"));
                    } catch (Exception error) {
                        runOnUiThread(() -> toast("保存 TXT 失败，请重试或使用复制日志"));
                    }
                });
            }
            return;
        }
        refreshStatus();
        if (request == ROLE_REQUEST) {
            continueAfterSystemRoleRequest();
        } else if (request == DEFAULT_APPS_REQUEST) {
            if (dialerRoleHeld()) checkAVFEnvironmentAtStartup();
            else {
                toast("AirSIM 仍未获得默认电话角色");
                completeStartupSetup();
            }
        } else if (request == SHIZUKU_SETUP_REQUEST) {
            startupSetupInFlight = false;
            startupSetupPending = true;
            scheduleStartupSetup();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (hasVoBLEPermissions()) {
            com.airsim.phonecontrol.voble.VoBLEGatewayService.start(this);
        }
        if (startupSetupPending) scheduleStartupSetup();
        if (currentPage == Page.SETTINGS && !voBLEAuthenticationVisible) showPage(Page.SETTINGS);
        if (currentPage == Page.STATUS && pcmStatusPoller != null) pcmStatusPoller.start();
        if (voBLEAuthenticationVisible) handler.post(voBLEAuthenticationRefresh);
        refreshShizukuStatus();
		startSMSInboxBaselineIfAuthorized();
        if (startupSetupCompleted && !startupSetupPending && !startupSetupInFlight) {
            handler.post(this::maybeShowCrashReportPrompt);
        }
    }

    @Override protected void onPause() {
        resumed = false;
        if (pcmStatusPoller != null) pcmStatusPoller.stop();
        handler.removeCallbacks(voBLEAuthenticationRefresh);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (bottomBar != null && bottomBar.getVisibility() == View.GONE) {
            stopPairingServer();
            showPage(Page.STATUS);
            return;
        }
        if (currentPage != Page.STATUS) {
            showPage(Page.STATUS);
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(startupSetup);
        if (shizukuStartupDialog != null) {
            shizukuStartupDialog.setOnDismissListener(null);
            shizukuStartupDialog.dismiss();
        }
        if (pcmStatusPoller != null) pcmStatusPoller.stop();
        handler.removeCallbacks(debugRefresh);
        handler.removeCallbacks(voBLEAuthenticationRefresh);
        stopPairingServer();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(SHIZUKU_STARTUP_CHECKED, shizukuStartupPolicy.checked());
        super.onSaveInstanceState(outState);
    }
}
