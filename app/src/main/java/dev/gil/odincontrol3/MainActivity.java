package dev.gil.odincontrol3;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView serviceStatus;
    private LinearLayout slotsArea;
    private TextView keyStatus;
    private DockPreviewView dockPreview;
    private String lastDisplayedLearning = "";
    private ScrollView scrollView;
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private int activeTab = 0;
    private TextView oemKeyStatus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Design.BG);
        getWindow().setNavigationBarColor(Design.BG);
        prefs = Prefs.get(this);
        if (!prefs.getBoolean("migrated_v03_dock", false)) {
            // The old v0.2 HOME_ONLY setting was forcibly enabled. In v0.3 the
            // launcher is recognized separately and visibility is user-controlled.
            prefs.edit().putInt(Prefs.VISIBILITY_MODE, VisibilityPolicy.HOME_ONLY)
                .putBoolean(Prefs.HIDE_RECENTS, true)
                .putBoolean("migrated_v03_dock", true).apply();
        }
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        DockKeepAliveService.setEnabled(this, prefs.getBoolean(Prefs.ENABLED, false));
        if (serviceStatus != null) refreshServiceStatus();
        if (keyStatus != null) showLatestKey();
        // Source learning may finish while the system HOME button backgrounds settings.
        if (root != null && Prefs.status(this).getString("last_learn", "").startsWith("등록됨")) {
            String learned = Prefs.status(this).getString("last_learn", "");
            if (!learned.equals(lastDisplayedLearning)) {
                lastDisplayedLearning = learned;
                buildUi();
            }
        }
    }
    @Override protected void onPause() {
        DockKeepAliveService.setEnabled(this, prefs.getBoolean(Prefs.ENABLED, false));
        super.onPause();
    }
    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, DockAccessibilityService.class);
        for (String part : enabled.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(part);
            if (me.equals(c)) return true;
        }
        return false;
    }
    private void refreshServiceStatus() {
        boolean ok = isServiceEnabled();
        serviceStatus.setText(ok ? "●  접근성 연결됨 · 독 사용 가능" : "●  접근성 허용이 필요해요");
        serviceStatus.setTextColor(ok ? Design.CYAN : Color.rgb(255, 191, 121));
    }

    private void buildUi() {
        final int previousScrollY = scrollView == null ? 0 : scrollView.getScrollY();
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Design.BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(16), dp(22), dp(12));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = Design.text(this, "GIL LABS  /  AYN ODIN 3", 10, Design.CYAN, true);
        eyebrow.setLetterSpacing(.18f);
        brand.addView(eyebrow);
        TextView title = Design.text(this, "Odin Control", 25, Design.TEXT, true);
        brand.addView(title);
        TextView subtitle = Design.text(this, "독 · 버튼 · 시스템을 한곳에서", 12, Design.DIM, false);
        brand.addView(subtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        serviceStatus = Design.text(this, "● 상태 확인 중", 12, Design.DIM, true);
        serviceStatus.setGravity(Gravity.CENTER);
        serviceStatus.setPadding(dp(14), dp(10), dp(14), dp(10));
        serviceStatus.setBackground(Design.bg(Design.PANEL, Design.PANEL2, 18, this));
        header.addView(serviceStatus);
        shell.addView(header);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(16), dp(5), dp(16), dp(12));
        String[] tabs = {"독 스튜디오", "버튼 매핑", "앱 슬롯", "오딘3 도구"};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            Button tab = button(tabs[i], activeTab == i);
            tab.setTextSize(13);
            tab.setMinHeight(dp(42));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            nav.addView(tab, lp);
            tab.setOnClickListener(v -> {
                activeTab = index;
                scrollView = null; // a new workspace always opens at its top
                buildUi();
            });
        }
        shell.addView(nav);

        ScrollView scroll = new ScrollView(this);
        scrollView = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(44));
        scroll.addView(root);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);

        if (previousScrollY > 0) scroll.post(() -> scroll.scrollTo(0, previousScrollY));
        switch (activeTab) {
            case 1: buildButtonStudioPage(); break;
            case 2: buildAppsPage(); break;
            case 3: buildToolsPage(); break;
            default: buildDockStudioPage(); break;
        }
        refreshServiceStatus();
    }

    private void buildDockStudioPage() {
        section("DOCK STUDIO", "독바 디자인 스튜디오");
        TextView intro = Design.text(this,
                "색상과 크기를 바꾸면 위 미리보기에 즉시 반영돼. 실제 독과 같은 슬롯·시스템 버튼 구성을 보여줘.",
                13, Design.DIM, false);
        intro.setLineSpacing(dp(2), 1.0f);
        root.addView(intro);
        margin(intro, 0, 0, 0, 14);

        boolean wide = getResources().getConfiguration().screenWidthDp >= 760;
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL);
        if (wide) {
            LinearLayout.LayoutParams col = new LinearLayout.LayoutParams(0, -2, 1);
            col.setMargins(0, 0, dp(8), 0); columns.addView(left, col);
            LinearLayout.LayoutParams col2 = new LinearLayout.LayoutParams(0, -2, 1);
            col2.setMargins(dp(8), 0, 0, 0); columns.addView(right, col2);
            root.addView(columns);
        } else {
            root.addView(columns);
            columns.addView(left, new LinearLayout.LayoutParams(-1, -2));
            columns.addView(right, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout previewCard = panelIn(left);
        cardTitle(previewCard, "LIVE PREVIEW", "실시간 독 미리보기", "현재 저장된 앱 아이콘과 독 모양을 반영");
        dockPreview = new DockPreviewView(this);
        previewCard.addView(dockPreview, new LinearLayout.LayoutParams(-1, dp(218)));
        LinearLayout previewActions = new LinearLayout(this);
        previewActions.setOrientation(LinearLayout.HORIZONTAL);
        Button enable = button(prefs.getBoolean(Prefs.ENABLED, false) ? "독 사용 중" : "독 켜기", true);
        Button access = button("접근성 권한", false);
        previewActions.addView(enable, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams accessLp = new LinearLayout.LayoutParams(0, -2, 1);
        accessLp.setMargins(dp(8), 0, 0, 0);
        previewActions.addView(access, accessLp);
        previewCard.addView(previewActions);
        enable.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean(Prefs.ENABLED, false);
            prefs.edit().putBoolean(Prefs.ENABLED, next).apply();
            DockKeepAliveService.setEnabled(this, next);
            DockAccessibilityService.requestUiRefresh();
            buildUi();
        });
        access.setOnClickListener(v -> Launch.accessibility(this));
        addPresetRow(previewCard);

        LinearLayout layoutCard = panelIn(left);
        cardTitle(layoutCard, "LAYOUT", "크기와 배치", "화면에 맞게 독의 크기와 위치를 조절");
        addSegmented(layoutCard, "정렬", new String[]{"왼쪽", "가운데", "오른쪽"},
                Prefs.clamp(prefs.getInt(Prefs.DOCK_ALIGN, 1), 0, 2), value -> {
                    prefs.edit().putInt(Prefs.DOCK_ALIGN, value).apply(); refreshPreview();
                });
        slider(layoutCard, "아이콘 크기", Prefs.ICON_SIZE, 32, 72, 48, " dp");
        slider(layoutCard, "아이콘 간격", Prefs.DOCK_GAP, 0, 18, 4, " dp");
        slider(layoutCard, "독 너비 · 0은 자동", Prefs.DOCK_WIDTH, 0, 850, 0, " dp");
        slider(layoutCard, "화면 아래 여백", Prefs.BOTTOM_OFFSET, 0, 120, 12, " dp");

        LinearLayout colors = panelIn(right);
        cardTitle(colors, "COLOR LAB", "컬러와 투명도", "색상칩을 눌러 적용하고 HEX로 세밀하게 조정");
        addColorPalette(colors, "독 배경", Prefs.DOCK_COLOR,
                new int[]{0xFF111D31,0xFF111217,0xFF26364B,0xFF153D64,0xFF433360,0xFF164B46,0xFF4B303A,0xFF2B3442},
                new String[]{"네이비","블랙","슬레이트","블루","퍼플","그린","와인","스틸"}, 0xFF111D31);
        addColorPalette(colors, "아이콘 타일", Prefs.ICON_TILE_COLOR,
                new int[]{0xFF273B4D,0xFF153C52,0xFF3B315B,0xFF284A3A,0xFF4B3B2C,0xFF30333A,0xFF5A3348},
                new String[]{"스틸","오션","바이올렛","포레스트","브론즈","차콜","베리"}, 0xFF273B4D);
        addColorPalette(colors, "아이콘 테두리", Prefs.ICON_BORDER_COLOR,
                new int[]{0xFF70DDE5,0xFF5DA9FF,0xFFB28CFF,0xFF72D6A0,0xFFFFC46B,0xFFFF8D8D,0xFFE8EEF5},
                new String[]{"시안","블루","라일락","민트","골드","코랄","화이트"}, 0xFF70DDE5);
        slider(colors, "배경 불투명도", Prefs.OPACITY, 15, 100, 82, "%");
        slider(colors, "독 모서리 둥글기", Prefs.DOCK_RADIUS, 0, 40, 23, " dp");

        LinearLayout iconCard = panelIn(right);
        cardTitle(iconCard, "ICON STYLE", "아이콘 스타일", "아이콘 타일, 테두리, 이름 표시를 세밀하게 설정");
        addSegmented(iconCard, "아이콘 모양", new String[]{"둥근 사각형", "원형", "각진 사각형"},
                Prefs.clamp(prefs.getInt(Prefs.ICON_SHAPE, 0), 0, 2), value -> {
                    prefs.edit().putInt(Prefs.ICON_SHAPE, value).apply(); refreshPreview();
                });
        slider(iconCard, "아이콘 내부 이미지 크기", Prefs.ICON_CONTENT, 35, 100, 88, "%");
        slider(iconCard, "테두리 굵기", Prefs.ICON_BORDER, 0, 5, 0, " dp");
        addSwitch(iconCard, "아이콘 배경색 통일", "앱 아이콘 타일에 선택한 색상을 적용", Prefs.ICON_UNIFY_BG, false);
        addSwitch(iconCard, "앱 이름 표시", "앱 아이콘 아래 이름 표시", Prefs.DOCK_LABELS, false);

        LinearLayout elements = panelIn(right);
        cardTitle(elements, "DOCK ITEMS", "독에 표시할 버튼", "필요한 버튼만 켜서 독을 깔끔하게 구성");
        addSwitch(elements, "홈 버튼", "독에서 홈 화면으로 이동", Prefs.SHOW_HOME, true);
        addSwitch(elements, "뒤로가기 버튼", "독에서 뒤로가기 실행", Prefs.SHOW_BACK, true);
        addSwitch(elements, "최근 앱 버튼", "최근 실행 앱 화면 열기", Prefs.SHOW_RECENTS, true);
        addSwitch(elements, "설정 버튼", "Gil Odin Control 설정 열기", Prefs.SHOW_SETTINGS, true);
        addSwitch(elements, "최근 앱 모두 정리 버튼", "독 오른쪽에 별도 정리 버튼 표시", Prefs.SHOW_CLEAR, true);

        LinearLayout behavior = panel();
        cardTitle(behavior, "VISIBILITY", "독 표시 규칙", "앱이나 게임에 따라 독을 자동으로 표시하거나 숨김");
        addSegmented(behavior, "표시 범위", new String[]{"홈·설정", "게임 제외", "모든 앱"},
                Prefs.clamp(prefs.getInt(Prefs.VISIBILITY_MODE, 0), 0, 2), value -> {
                    prefs.edit().putInt(Prefs.VISIBILITY_MODE, value).apply(); buildUi();
                });
        addSwitch(behavior, "최근 앱 화면에서 숨김", "작업 관리자·최근 실행 화면에서는 독을 감춤", Prefs.HIDE_RECENTS, true);
        quickButton(behavior, "독을 숨길 앱 추가", "특정 앱을 실행하면 독 자동 숨김", this::addHiddenApp);
        quickButton(behavior, "숨김 앱 목록 관리", "등록된 앱을 목록에서 제거", this::removeHiddenApp);
    }

    private void buildButtonStudioPage() {
        section("INPUT STUDIO", "버튼 매핑 스튜디오");
        TextView intro = Design.text(this,
                "물리 버튼의 원본 신호와 실행 동작을 분리해서 설정해. 같은 신호를 두 버튼에 중복 등록하지 않으며, RetroArch 조합키는 원본 DOWN/UP 유지 옵션을 제공해.",
                13, Design.DIM, false);
        intro.setLineSpacing(dp(2), 1.0f); root.addView(intro); margin(intro,0,0,0,14);

        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL);
        boolean wide = getResources().getConfiguration().screenWidthDp >= 760;
        LinearLayout first = new LinearLayout(this); first.setOrientation(LinearLayout.VERTICAL);
        LinearLayout second = new LinearLayout(this); second.setOrientation(LinearLayout.VERTICAL);
        if (wide) {
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0,-2,1); a.setMargins(0,0,dp(8),0); top.addView(first,a);
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0,-2,1); b.setMargins(dp(8),0,0,0); top.addView(second,b);
        } else { top.setOrientation(LinearLayout.VERTICAL); top.addView(first); top.addView(second); }
        root.addView(top);
        addMappingCard(first, RemapPolicy.M1, "M1", Prefs.BUTTON_M1, 0xFF55C7D1);
        addMappingCard(first, RemapPolicy.M2, "M2", Prefs.BUTTON_M2, 0xFF9B83E8);
        addMappingCard(second, RemapPolicy.BACK, "뒤로가기", Prefs.BUTTON_BACK, 0xFFFFB86B);
        addMappingCard(second, RemapPolicy.HOME, "홈 버튼", Prefs.BUTTON_HOME, 0xFF79C995);

        LinearLayout oem = panel();
        cardTitle(oem, "ODIN OEM KEYCODE", "M1 / M2 순정 출력 키", "OEM 설정에서 M1·M2가 내보낼 게임패드 키코드를 변경해. 변경 후 물리 신호를 다시 학습해야 해.");
        oemKeyStatus = Design.text(this, "현재 OEM 키값 읽는 중…", 13, Design.CYAN, true);
        oem.addView(oemKeyStatus); margin(oemKeyStatus,0,0,0,10);
        LinearLayout oemRow = new LinearLayout(this); oemRow.setOrientation(LinearLayout.HORIZONTAL);
        Button m1 = button("M1 키코드 선택", false), m2 = button("M2 키코드 선택", false);
        oemRow.addView(m1,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams m2p = new LinearLayout.LayoutParams(0,-2,1); m2p.setMargins(dp(8),0,0,0); oemRow.addView(m2,m2p);
        oem.addView(oemRow);
        m1.setOnClickListener(v -> chooseOemKeycode(true));
        m2.setOnClickListener(v -> chooseOemKeycode(false));
        LinearLayout captureRow = new LinearLayout(this); captureRow.setOrientation(LinearLayout.HORIZONTAL);
        Button captureM1 = button("M1 직접 누르기", false), captureM2 = button("M2 직접 누르기", false);
        captureRow.addView(captureM1,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams captureM2p=new LinearLayout.LayoutParams(0,-2,1); captureM2p.setMargins(dp(8),0,0,0); captureRow.addView(captureM2,captureM2p);
        oem.addView(captureRow); margin(captureRow,0,8,0,0);
        captureM1.setOnClickListener(v -> captureOemKeycode(true));
        captureM2.setOnClickListener(v -> captureOemKeycode(false));
        quickButton(oem, "M1/M2를 BUTTON_1·BUTTON_2로 설정", "RetroArch에서 서로 다른 무기능 핫키 버튼으로 쓰기 좋은 기본 조합", this::applyNeutralOemKeys);
        quickButton(oem, "변경 전 OEM 키코드 복원", "이 앱이 최초 변경 전에 저장한 값으로 되돌리기", this::restoreOemButtons);
        refreshOemKeyStatus();

        LinearLayout behavior = panel();
        cardTitle(behavior, "INPUT BEHAVIOR", "입력 처리 옵션", "게임과 에뮬레이터에서 원본 입력을 유지할지 선택");
        addSwitch(behavior, "게임 실행 중 소프트웨어 매핑 통과", "켜면 게임에서 M1/M2/BACK/HOME 동작을 변경하지 않고 원본 신호를 전달", Prefs.GAME_PASSTHROUGH, false);
        addSwitch(behavior, "RetroArch에서 BACK 원본 유지", "BACK을 핫키 조합키로 사용할 때 DOWN/UP을 그대로 전달", Prefs.RETROARCH_BACK_PASSTHROUGH, true);
        addSwitch(behavior, "접근성 키 이벤트 필터", "물리 버튼 매핑을 감지하려면 활성화 필요", Prefs.KEY_FILTER, true);
        keyStatus = Design.text(this, "최근 입력: 아직 없음", 13, Design.TEXT, true);
        keyStatus.setPadding(dp(12),dp(12),dp(12),dp(12));
        keyStatus.setBackground(Design.bg(Design.PANEL2, 0, 12, this));
        behavior.addView(keyStatus); margin(keyStatus,0,8,0,10); showLatestKey();
        Button test = button("10초 입력 진단 시작", true); behavior.addView(test);
        test.setOnClickListener(v -> startInputDiagnostic());
        quickButton(behavior, "소프트웨어 동작 초기화", "OEM 키코드와 학습된 물리 신호는 보존", this::resetButtonActions);
    }

    private void buildAppsPage() {
        section("APP SHORTCUTS", "독 앱 슬롯 관리");
        TextView intro = Design.text(this, "독에 넣을 앱을 선택하고 순서를 정리해. 앱 아이콘은 실제 설치된 아이콘으로 독과 미리보기에 표시돼.", 13, Design.DIM, false);
        root.addView(intro); margin(intro,0,0,0,14);
        slotsArea = panel();
        refreshSlots();
        LinearLayout info = panel();
        cardTitle(info, "TIP", "독 구성 팁", "홈·뒤로가기·최근 앱 등 시스템 버튼과 앱 슬롯은 실제 독에서 같은 순서로 배치돼.");
        quickButton(info, "독 스튜디오로 이동", "크기·색상·표시 항목 설정", () -> { activeTab = 0; buildUi(); });
    }

    private void buildToolsPage() {
        section("ODIN 3 TOOLS", "오딘3 시스템 제어");
        LinearLayout status = panel();
        cardTitle(status, "DEVICE STATUS", "기기 및 서비스 상태", "현재 접근성 연결과 OEM 제어 서비스 확인");
        TextView device = Design.text(this, android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                "  ·  Android " + android.os.Build.VERSION.RELEASE, 13, Design.DIM, true);
        status.addView(device);
        TextView pserver = Design.text(this, "PServer: " + new PServerBridge().status(), 13, Design.CYAN, true);
        status.addView(pserver); margin(pserver,0,8,0,0);
        Button access = button("접근성 서비스 설정", true); status.addView(access); access.setOnClickListener(v -> Launch.accessibility(this));

        LinearLayout nativeTools = panel();
        cardTitle(nativeTools, "ODIN OEM", "OdinTools 기능", "오딘3 순정 PServer를 사용하는 성능·팬·컨트롤러·트리거·디스플레이·진동·충전 설정");
        quickButton(nativeTools, "오딘3 시스템 제어 열기", "통합된 OdinTools 화면 열기", () -> startActivity(new Intent().setClassName(getPackageName(), "de.langerhans.odintools.main.MainActivity")));
        TextView note = Design.text(this, "앱별 성능 프로필은 별도 접근성 서비스의 권한이 필요할 수 있어. OEM PServer가 응답하지 않으면 설정은 적용되지 않아.", 12, Design.DIM, false);
        nativeTools.addView(note);

        LinearLayout system = panel();
        cardTitle(system, "ANDROID", "안드로이드 설정", "무선·디스플레이·배터리 설정으로 바로 이동");
        quickButton(system, "시스템 설정", "Wi-Fi·디스플레이·배터리 등", () -> Launch.settings(this));
        quickButton(system, "Wi-Fi 설정", "무선 연결", () -> Launch.wifi(this));
        quickButton(system, "블루투스 설정", "무선 입력장치", () -> Launch.bluetooth(this));

        LinearLayout about = panel();
        cardTitle(about, "SUPPORT", "진단 및 지원", "문제가 생기면 아래 진단 정보를 복사해 전달해줘.");
        Button report = button("진단 정보 복사", false); about.addView(report); report.setOnClickListener(v -> copyDiagnostics());
    }

    private void addPresetRow(LinearLayout parent) {
        TextView label = Design.text(this, "빠른 디자인 프리셋", 13, Design.TEXT, true);
        parent.addView(label); margin(label,0,12,0,7);
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Midnight", "Carbon", "Aurora", "Retro"};
        int[] colors = {0xFF111D31,0xFF111217,0xFF35264F,0xFF173D38};
        for (int i=0;i<names.length;i++) {
            final int preset=i;
            Button b=button("●  "+names[i],false); b.setTextColor(Color.WHITE);
            b.setBackground(Design.bg(colors[i], 0xFF52677E, 12, this));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.setMargins(0,0,dp(8),0); row.addView(b,lp);
            b.setOnClickListener(v -> applyDockPreset(preset));
        }
        scroll.addView(row); parent.addView(scroll);
    }

    private void applyDockPreset(int preset) {
        int[] dock={0xFF111D31,0xFF111217,0xFF35264F,0xFF173D38};
        int[] tile={0xFF273B4D,0xFF30333A,0xFF493B68,0xFF285B50};
        int[] border={0xFF70DDE5,0xFF69727D,0xFFB28CFF,0xFF83D6A8};
        int[] opacity={88,94,86,90};
        int[] radius={24,16,30,20};
        prefs.edit().putInt(Prefs.DOCK_COLOR,dock[preset]).putInt(Prefs.ICON_TILE_COLOR,tile[preset])
                .putInt(Prefs.ICON_BORDER_COLOR,border[preset]).putInt(Prefs.OPACITY,opacity[preset])
                .putInt(Prefs.DOCK_RADIUS,radius[preset]).putInt(Prefs.ICON_SHAPE,0)
                .putInt(Prefs.ICON_BORDER,1).putBoolean(Prefs.ICON_UNIFY_BG,true).apply();
        refreshPreview();
        DockAccessibilityService.requestUiRefresh();
        Toast.makeText(this,"디자인 프리셋 적용됨",Toast.LENGTH_SHORT).show();
        buildUi();
    }

    private void addColorPalette(LinearLayout parent, String title, String key, int[] colors, String[] names, int fallback) {
        TextView label=Design.text(this,title,13,Design.TEXT,true); parent.addView(label); margin(label,0,12,0,5);
        HorizontalScrollView scroller=new HorizontalScrollView(this); scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        int selected=prefs.getInt(key,fallback);
        for(int i=0;i<colors.length;i++) {
            final int color=colors[i];
            LinearLayout chip=new LinearLayout(this); chip.setOrientation(LinearLayout.VERTICAL); chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(7),dp(6),dp(7),dp(5));
            TextView swatch=Design.text(this, selected==color ? "✓" : "", 14, Color.WHITE, true);
            swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(Design.bg(color,selected==color?Design.CYAN:0xFF50647A,18,this));
            chip.addView(swatch,new LinearLayout.LayoutParams(dp(36),dp(30)));
            TextView name=Design.text(this,names[i],10,Design.DIM,false); name.setGravity(Gravity.CENTER);
            chip.addView(name);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(68),-2); lp.setMargins(0,0,dp(4),0); row.addView(chip,lp);
            chip.setOnClickListener(v -> {
                SharedPreferences.Editor e=prefs.edit().putInt(key,color);
                if(Prefs.ICON_TILE_COLOR.equals(key)) e.putBoolean(Prefs.ICON_UNIFY_BG,true);
                if(Prefs.ICON_BORDER_COLOR.equals(key) && prefs.getInt(Prefs.ICON_BORDER,0)==0) e.putInt(Prefs.ICON_BORDER,1);
                e.apply(); DockAccessibilityService.requestUiRefresh(); buildUi();
            });
        }
        TextView custom=Design.text(this,"HEX 직접 입력",11,Design.CYAN,true); custom.setGravity(Gravity.CENTER);
        custom.setPadding(dp(12),0,dp(12),0); custom.setBackground(Design.bg(Design.PANEL2,Design.PANEL2,12,this));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(58)); cp.setMargins(dp(4),0,0,0); row.addView(custom,cp);
        custom.setOnClickListener(v -> inputHexColor(key,title,fallback));
        scroller.addView(row); parent.addView(scroller);
    }

    private interface IntChoice { void accept(int value); }
    private void addSegmented(LinearLayout parent,String title,String[] labels,int selected,IntChoice action) {
        TextView label=Design.text(this,title,13,Design.TEXT,true); parent.addView(label); margin(label,0,10,0,5);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<labels.length;i++) {
            final int value=i; Button b=button(labels[i],i==selected); b.setTextSize(12); b.setMinHeight(dp(40));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(0,0,dp(5),0); row.addView(b,lp);
            b.setOnClickListener(v -> action.accept(value));
        }
        parent.addView(row); margin(row,0,0,0,12);
    }

    private LinearLayout panelIn(LinearLayout parent) {
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(17),dp(17),dp(17),dp(17)); p.setBackground(Design.bg(Design.PANEL,Design.PANEL2,20,this));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(14)); parent.addView(p,lp); return p;
    }
    private void cardTitle(LinearLayout card,String kicker,String title,String detail) {
        TextView k=Design.text(this,kicker,10,Design.CYAN,true); k.setLetterSpacing(.14f); card.addView(k); margin(k,0,0,0,5);
        card.addView(Design.text(this,title,18,Design.TEXT,true));
        TextView d=Design.text(this,detail,12,Design.DIM,false); d.setLineSpacing(dp(2),1f); card.addView(d); margin(d,0,3,0,12);
    }
    private void refreshPreview() {
        if (dockPreview != null) dockPreview.invalidate();
        DockAccessibilityService.requestUiRefresh();
    }

    private void addMappingCard(LinearLayout parent,int source,String label,String pref,int accent) {
        LinearLayout card=panelIn(parent);
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot=Design.text(this,"●",16,accent,true); head.addView(dot);
        TextView name=Design.text(this,"  "+label,18,Design.TEXT,true); head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=Design.text(this,physicalSignal(source).startsWith("신호 미등록")?"미학습":"신호 등록됨",11,
                physicalSignal(source).startsWith("신호 미등록")?0xFFFFC46B:0xFF79C995,true);
        head.addView(status); card.addView(head);
        TextView signal=Design.text(this,physicalSignal(source),12,Design.DIM,false); card.addView(signal); margin(signal,0,4,0,7);
        TextView current=Design.text(this,"실행 동작  ·  "+actionName(pref),14,Design.CYAN,true); card.addView(current); margin(current,0,0,0,10);
        LinearLayout buttons=new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button learn=button("신호 학습",false), action=button("동작 설정",true);
        buttons.addView(learn,new LinearLayout.LayoutParams(0,-2,1)); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1); ap.setMargins(dp(7),0,0,0); buttons.addView(action,ap);
        card.addView(buttons);
        learn.setOnClickListener(v -> capturePhysicalSignal(source,label));
        action.setOnClickListener(v -> choosePhysicalAction(pref,label,source));
    }

    private void refreshOemKeyStatus() {
        if(oemKeyStatus==null) return;
        oemKeyStatus.setText("OEM 키코드 읽는 중…");
        io.execute(() -> {
            String result;
            try { PServerBridge bridge=new PServerBridge(); int m1=bridge.readKey(true),m2=bridge.readKey(false);
                result="M1  ·  "+keyCodeLabel(m1)+"       M2  ·  "+keyCodeLabel(m2);
            } catch(Exception e) { result="OEM 키값을 읽지 못함 · "+e.getMessage(); }
            final String text=result; runOnUiThread(() -> { if(oemKeyStatus!=null) oemKeyStatus.setText(text); });
        });
    }
    private String keyCodeLabel(int code) { return android.view.KeyEvent.keyCodeToString(code)+" ("+code+")"; }
    private int[] supportedOemKeycodes() {
        int[] codes=new int[3+16+12]; int n=0; codes[n++]=3; codes[n++]=4; codes[n++]=187;
        for(int i=188;i<=203;i++) codes[n++]=i;
        for(int i=131;i<=142;i++) codes[n++]=i;
        return codes;
    }
    private void chooseOemKeycode(boolean m1) {
        int[] codes=supportedOemKeycodes(); String[] labels=new String[codes.length];
        for(int i=0;i<codes.length;i++) labels[i]=keyCodeLabel(codes[i]);
        new AlertDialog.Builder(this).setTitle((m1?"M1":"M2")+" 출력 키코드")
                .setItems(labels,(d,which)->saveOemKeycode(m1,codes[which]))
                .setNegativeButton("취소",null).show();
    }
    private boolean isSupportedOemKeycode(int code) {
        if(code==3 || code==4 || code==187) return true;
        return (code>=188 && code<=203) || (code>=131 && code<=142);
    }
    private void captureOemKeycode(boolean m1) {
        final boolean previousFilter=prefs.getBoolean(Prefs.KEY_FILTER,true);
        // Let the focused dialog receive the hardware key instead of the accessibility filter.
        prefs.edit().putBoolean(Prefs.KEY_FILTER,false).apply();
        DockAccessibilityService.refreshKeyOptionsIfConnected();
        TextView prompt=Design.text(this,"변경할 게임패드 버튼을 눌러줘.\n\n지원되는 키만 저장할 수 있어.\n취소하면 기존 값은 그대로 유지돼.",15,Design.TEXT,false);
        prompt.setPadding(dp(22),dp(24),dp(22),dp(24));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle((m1?"M1":"M2")+" 키 입력 학습")
                .setView(prompt).setNegativeButton("취소",null).create();
        dialog.setOnKeyListener((d,keyCode,event)->{
            if(event.getAction()==android.view.KeyEvent.ACTION_UP && event.getRepeatCount()==0) {
                if(isSupportedOemKeycode(keyCode)) {
                    d.dismiss(); saveOemKeycode(m1,keyCode);
                } else {
                    prompt.setText("이 키코드는 Odin OEM에서 지원하지 않아.\n\n감지: "+keyCodeLabel(keyCode)+"\n다른 게임패드 버튼을 눌러줘.");
                }
            }
            return true;
        });
        dialog.setOnDismissListener(d->{
            prefs.edit().putBoolean(Prefs.KEY_FILTER,previousFilter).apply();
            DockAccessibilityService.refreshKeyOptionsIfConnected();
        });
        dialog.setOnShowListener(d->{
            if(dialog.getWindow()!=null) {
                dialog.getWindow().getDecorView().setFocusableInTouchMode(true);
                dialog.getWindow().getDecorView().requestFocus();
            }
        });
        dialog.show();
        if(dialog.getWindow()!=null) {
            dialog.getWindow().getDecorView().setFocusableInTouchMode(true);
            dialog.getWindow().getDecorView().requestFocus();
        }
    }
    private void saveOemKeycode(boolean m1,int code) {
        io.execute(() -> {
            String message;
            try {
                PServerBridge bridge=new PServerBridge();
                int other=bridge.readKey(!m1);
                if(other==code) throw new IllegalArgumentException("M1과 M2는 서로 다른 키코드를 사용해야 해.");
                int old=bridge.readKey(m1);
                String backup=m1?"oem_m1_backup":"oem_m2_backup";
                if(!prefs.contains(backup)) prefs.edit().putInt(backup,old).commit();
                if(!bridge.writeKey(m1,code)) throw new IllegalStateException("OEM 저장값 확인 실패");
                int source=m1?RemapPolicy.M1:RemapPolicy.M2; String prefix=Prefs.PHYSICAL_PREFIX+source;
                prefs.edit().remove(prefix+"_key").remove(prefix+"_scan").remove(prefix+"_descriptor").apply();
                DockAccessibilityService.refreshKeyOptionsIfConnected();
                message=(m1?"M1":"M2")+" 출력 키를 "+keyCodeLabel(code)+"로 변경했어. 물리 입력 신호를 다시 학습해줘.";
            } catch(Exception e) { message="키코드 변경 실패: "+e.getMessage(); }
            final String result=message;
            runOnUiThread(() -> { Toast.makeText(this,result,Toast.LENGTH_LONG).show(); refreshOemKeyStatus(); buildUi(); });
        });
    }

    private void startInputDiagnostic() {
        prefs.edit().putLong(Prefs.KEY_TEST_UNTIL,System.currentTimeMillis()+10000L).apply();
        Toast.makeText(this,"10초 동안 M1/M2/BACK/HOME을 눌러봐. 입력 신호를 기록할게.",Toast.LENGTH_LONG).show();
        final android.os.Handler h=new android.os.Handler(getMainLooper()); final Runnable[] poll=new Runnable[1];
        poll[0]=() -> { showLatestKey(); if(!isFinishing() && prefs.getLong(Prefs.KEY_TEST_UNTIL,0L)>System.currentTimeMillis()) h.postDelayed(poll[0],300); };
        h.post(poll[0]);
    }

    private String visibilityLabel() {
        int mode = prefs.getInt(Prefs.VISIBILITY_MODE, VisibilityPolicy.HOME_ONLY);
        return mode == 0 ? "홈·설정에서만 표시" :
               mode == 1 ? "홈·일반 앱 표시 / 게임 자동 숨김" : "모든 앱 표시 (최근 앱은 별도 숨김)";
    }
    private void chooseVisibility() {
        String[] names = {"홈·설정에서만 표시", "게임을 제외한 앱에서 표시", "모든 앱에서 표시"};
        new AlertDialog.Builder(this).setTitle("독 표시 범위")
            .setSingleChoiceItems(names, Prefs.clamp(prefs.getInt(Prefs.VISIBILITY_MODE, 0), 0, 2), (d, i) -> {
                prefs.edit().putInt(Prefs.VISIBILITY_MODE, i).apply();
                DockAccessibilityService.requestUiRefresh();
                d.dismiss(); buildUi();
            }).setNegativeButton("취소", null).show();
    }
    private String alignLabel() {
        String[] positions = {"왼쪽", "가운데", "오른쪽"};
        return positions[Prefs.clamp(prefs.getInt(Prefs.DOCK_ALIGN, 1), 0, 2)];
    }
    private void chooseAlignment() {
        new AlertDialog.Builder(this).setTitle("독 정렬")
            .setSingleChoiceItems(new String[]{"왼쪽", "가운데", "오른쪽"},
                Prefs.clamp(prefs.getInt(Prefs.DOCK_ALIGN, 1), 0, 2), (d, i) -> {
                    prefs.edit().putInt(Prefs.DOCK_ALIGN, i).apply(); DockAccessibilityService.requestUiRefresh(); d.dismiss(); buildUi();
                }).setNegativeButton("취소", null).show();
    }
    private String iconShapeName() {
        String[] names = {"모서리 둥근 사각형", "원형", "각진 사각형"};
        return names[Prefs.clamp(prefs.getInt(Prefs.ICON_SHAPE, 0), 0, 2)];
    }
    private void chooseIconShape() {
        new AlertDialog.Builder(this).setTitle("아이콘 모양")
                .setSingleChoiceItems(new String[]{"둥근 사각형", "원형", "각진 사각형"},
                        Prefs.clamp(prefs.getInt(Prefs.ICON_SHAPE, 0), 0, 2),
                        (d, i) -> { prefs.edit().putInt(Prefs.ICON_SHAPE, i).apply(); DockAccessibilityService.requestUiRefresh(); d.dismiss(); buildUi(); })
                .setNegativeButton("취소", null).show();
    }
    private void inputHexColor(String pref, String title, int fallback) {
        EditText entry = new EditText(this);
        entry.setSingleLine(true);
        entry.setText(String.format(Locale.ROOT, "#%06X", prefs.getInt(pref, fallback) & 0xFFFFFF));
        new AlertDialog.Builder(this).setTitle(title).setView(entry)
                .setMessage("RGB 6자리, 예: #70DDE5")
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", (d, i) -> {
                    try {
                        String value = entry.getText().toString().trim();
                        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid HEX");
                        prefs.edit().putInt(pref, Color.parseColor(value)).apply(); DockAccessibilityService.requestUiRefresh(); buildUi();
                    } catch (Exception error) { Toast.makeText(this, "#RRGGBB 6자리로 입력해줘", Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void chooseDockColor() {
        String[] names = {"미드나잇 네이비", "차콜 블랙", "그레이", "오션 블루", "오로라 퍼플", "포레스트", "직접 입력 (HEX)"};
        int[] colors = {0xFF111D31, 0xFF111217, 0xFF3A3C44,
                        0xFF153D64, 0xFF433360, 0xFF164B46};
        new AlertDialog.Builder(this).setTitle("독 배경색")
            .setItems(names, (d, i) -> {
                if (i == colors.length) { inputDockColor(); return; }
                prefs.edit().putInt(Prefs.DOCK_COLOR, colors[i]).apply(); buildUi();
            })
            .setNegativeButton("취소", null).show();
    }
    private void inputDockColor() {
        EditText entry = new EditText(this);
        entry.setSingleLine(true);
        entry.setText(String.format(Locale.ROOT, "#%06X", prefs.getInt(Prefs.DOCK_COLOR, 0xFF111D31) & 0xFFFFFF));
        new AlertDialog.Builder(this).setTitle("배경색 직접 입력")
            .setMessage("예: #142738 · RGB 6자리")
            .setView(entry).setNegativeButton("취소", null)
            .setPositiveButton("적용", (d, i) -> {
                try {
                    String value = entry.getText().toString().trim();
                    if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("색상 형식");
                    prefs.edit().putInt(Prefs.DOCK_COLOR, Color.parseColor(value)).apply();
                    DockAccessibilityService.requestUiRefresh();
                    buildUi();
                } catch (Exception invalid) { Toast.makeText(this,"#RRGGBB 형식으로 입력해줘",Toast.LENGTH_LONG).show(); }
            }).show();
    }

    private void addHiddenApp() {
        chooseLaunchPackage("독을 숨길 앱 선택", pkg -> {
            String old = prefs.getString(Prefs.HIDDEN_APPS, "");
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            for (String a : old.split(";")) if (!a.isEmpty()) names.add(a);
            names.add(pkg);
            prefs.edit().putString(Prefs.HIDDEN_APPS, android.text.TextUtils.join(";", names)).apply();
            DockAccessibilityService.requestUiRefresh();
            Toast.makeText(this, "앱 자동 숨김에 추가됨", Toast.LENGTH_SHORT).show();
        });
    }
    private void removeHiddenApp() {
        String old = prefs.getString(Prefs.HIDDEN_APPS, "");
        ArrayList<String> apps = new ArrayList<>();
        for (String a : old.split(";")) if (!a.isEmpty()) apps.add(a);
        if (apps.isEmpty()) { Toast.makeText(this,"등록한 앱 없음",Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("자동 숨김 앱 제거")
            .setItems(apps.toArray(new String[0]), (d, index) -> {
                apps.remove(index);
                prefs.edit().putString(Prefs.HIDDEN_APPS, android.text.TextUtils.join(";", apps)).apply();
                DockAccessibilityService.requestUiRefresh();
            }).setNegativeButton("취소", null).show();
    }
    private void moveSlot() {
        final String[] labels = new String[Prefs.MAX_SLOTS];
        for (int i = 0; i < labels.length; i++)
            labels[i] = (i + 1) + "번: " + prefs.getString(Prefs.SLOT_PREFIX + i, "비어 있음");
        new AlertDialog.Builder(this).setTitle("이동할 슬롯")
            .setItems(labels, (d, from) -> new AlertDialog.Builder(this).setTitle("바꿀 위치")
                .setItems(labels, (dd, to) -> {
                    String a = prefs.getString(Prefs.SLOT_PREFIX + from, "");
                    String b = prefs.getString(Prefs.SLOT_PREFIX + to, "");
                    prefs.edit().putString(Prefs.SLOT_PREFIX + from, b)
                        .putString(Prefs.SLOT_PREFIX + to, a).apply();
                    DockAccessibilityService.requestUiRefresh();
                    buildUi();
                }).setNegativeButton("취소", null).show())
            .setNegativeButton("취소", null).show();
    }

    private void buildControllerSection() {
        LinearLayout p = panel();
        p.addView(Design.text(this,
                "물리 버튼을 먼저 식별하고, 동작은 별도로 지정해. 출력 결과가 BACK/HOME이어도 " +
                "물리 M1·M2·BACK·HOME의 원본 신호는 섞이지 않아.", 13, Design.DIM, false));
        addSwitch(p, "게임 실행 중에는 소프트웨어 매핑 사용 안 함", "선택하면 게임에서는 지정 동작보다 게임패드 원본 입력을 우선해. 기본값은 꺼짐.",
                Prefs.GAME_PASSTHROUGH, false);
        addSwitch(p, "RetroArch에서는 물리 뒤로가기 원본 유지", "RetroArch 핫키 조합에 뒤로가기 버튼을 쓰는 경우 별도 우선 적용",
                Prefs.RETROARCH_BACK_PASSTHROUGH, true);
        addSwitch(p, "물리 키 입력 필터", "끄면 앱에서 지정한 시스템 동작을 적용하지 않음", Prefs.KEY_FILTER, true);
        p.addView(Design.text(this, "① 물리 버튼 학습   ② 버튼 동작 선택", 14, Design.CYAN, true));
        addPhysicalRow(p, RemapPolicy.M1, "M1", Prefs.BUTTON_M1);
        addPhysicalRow(p, RemapPolicy.M2, "M2", Prefs.BUTTON_M2);
        addPhysicalRow(p, RemapPolicy.BACK, "뒤로가기", Prefs.BUTTON_BACK);
        addPhysicalRow(p, RemapPolicy.HOME, "홈", Prefs.BUTTON_HOME);
        quickButton(p, "RetroArch용 M1/M2 고유 키 설정", 
                "M1→BUTTON_1, M2→BUTTON_2 · OEM 설정을 백업하고 키를 직접 전달", this::applyNeutralOemKeys);
        quickButton(p, "M1/M2 변경 전 OEM 키코드 복원", "예전에 백업한 원래 값을 다시 적용", this::restoreOemButtons);
        p.addView(Design.text(this,
                "RetroArch 핫키: M1/M2의 키값을 OEM에서 서로 다른 BUTTON으로 지정하면 " +
                "키를 누른 채 조합하는 입력을 순정 게임패드에서 처리할 수 있어. " +
                "BACK을 홈으로 바꾸는 건 접근성에서 처리하지만, 물리 HOME→게임패드 고유 키 출력은 " +
                "순정에서 HOME 신호를 앱으로 전달하지 않을 경우 불가능해. " +
                "원본 HOME 신호가 잡히지 않으면 동작 변경을 적용했다고 표시하지 않아.",
                12, Design.DIM, false));
        keyStatus = Design.text(this, "버튼 진단: 아직 없음", 13, Design.CYAN, true);
        p.addView(keyStatus); showLatestKey();
        Button test = button("10초 물리 버튼 신호 진단", false);
        p.addView(test);
        test.setOnClickListener(v -> {
            prefs.edit().putLong(Prefs.KEY_TEST_UNTIL, System.currentTimeMillis() + 10000L).apply();
            Toast.makeText(this, "10초 동안 버튼을 눌러봐. 키코드와 스캔코드를 기록할게.", Toast.LENGTH_LONG).show();
            final android.os.Handler h = new android.os.Handler(getMainLooper());
            final Runnable[] poll = new Runnable[1];
            poll[0] = () -> {
                showLatestKey();
                if (!isFinishing() && prefs.getLong(Prefs.KEY_TEST_UNTIL, 0L) > System.currentTimeMillis())
                    h.postDelayed(poll[0], 350);
            };
            h.post(poll[0]);
        });
        quickButton(p, "소프트웨어 버튼 동작만 초기화", "OEM 키코드 및 학습한 물리 입력은 유지", this::resetButtonActions);
    }

    private String physicalSignal(int source) {
        String prefix = Prefs.PHYSICAL_PREFIX + source;
        int fallback = source == RemapPolicy.BACK ? 4 : source == RemapPolicy.HOME ? 3 : -1;
        int key = prefs.getInt(prefix + "_key", fallback);
        if (key < 0) return "신호 미등록 · [학습]으로 먼저 등록";
        int scan = prefs.getInt(prefix + "_scan", -1);
        return "원본 key=" + key + (scan >= 0 ? " / scan=" + scan : " / scan=자동");
    }

    private void addPhysicalRow(LinearLayout parent, int source, String title, String pref) {
        parent.addView(Design.text(this, title + "   ·   " + physicalSignal(source), 13, Design.TEXT, true));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button learn = button("① 입력 학습", false);
        Button action = button("② " + actionName(pref), true);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1);
        half.setMargins(0, dp(5), dp(6), dp(14));
        buttons.addView(learn, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -2, 1);
        half2.setMargins(dp(6), dp(5), 0, dp(14));
        buttons.addView(action, half2);
        parent.addView(buttons);
        learn.setOnClickListener(v -> capturePhysicalSignal(source, title));
        action.setOnClickListener(v -> choosePhysicalAction(pref, title, source));
    }

    private void capturePhysicalSignal(int source, String title) {
        prefs.edit().putInt(Prefs.LEARN_SOURCE, source)
                .putLong(Prefs.LEARN_UNTIL, System.currentTimeMillis() + 9000).apply();
        TextView content = Design.text(this, title + " 물리 버튼을 한 번 눌러줘.\n" +
                "다른 버튼과 입력이 같으면 자동으로 등록을 거부해.\n" +
                "HOME은 Android에서 먼저 처리될 수 있어.", 14, Design.TEXT, false);
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(22),dp(16),dp(22),dp(12));
        area.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("물리 버튼 원본 신호 학습")
                .setView(area).setNegativeButton("취소", (d,w) ->
                        prefs.edit().putInt(Prefs.LEARN_SOURCE,-1).putLong(Prefs.LEARN_UNTIL,0).apply())
                .create();
        dialog.setOnDismissListener(d -> {
            if (prefs.getInt(Prefs.LEARN_SOURCE,-1) == source)
                prefs.edit().putInt(Prefs.LEARN_SOURCE,-1).putLong(Prefs.LEARN_UNTIL,0).apply();
        });
        dialog.show();
        android.os.Handler h = new android.os.Handler(getMainLooper());
        final Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (!dialog.isShowing() || isFinishing()) return;
            if (prefs.getLong(Prefs.LEARN_UNTIL, 0) == 0) {
                String result = Prefs.status(this).getString("last_learn", "학습 종료");
                dialog.dismiss();
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                buildUi();
            } else if (prefs.getLong(Prefs.LEARN_UNTIL, 0) < System.currentTimeMillis()) {
                dialog.dismiss();
                Toast.makeText(this, "시간 초과. 이 버튼은 시스템이 먼저 처리했을 수 있어.", Toast.LENGTH_LONG).show();
            } else h.postDelayed(poll[0], 250);
        };
        h.postDelayed(poll[0], 300);
    }

    private static final String[] PHYSICAL_ACTIONS = {
        "원본 신호 유지", "무기능 (신호 포착 시 차단)", "홈 화면", "뒤로가기", "최근 앱", "알림창"
    };
    private String actionName(String preference) {
        int a = prefs.getInt(preference, RemapPolicy.PASS);
        return PHYSICAL_ACTIONS[Prefs.clamp(a, 0, PHYSICAL_ACTIONS.length - 1)];
    }
    private void choosePhysicalAction(String pref, String label, int source) {
        int selected = Prefs.clamp(prefs.getInt(pref, 0), 0, PHYSICAL_ACTIONS.length - 1);
        new AlertDialog.Builder(this).setTitle(label + " 버튼 동작")
            .setSingleChoiceItems(PHYSICAL_ACTIONS, selected, (dialog, which) -> {
                dialog.dismiss();
                savePhysicalAction(pref, source, which);
            }).setNegativeButton("취소", null).show();
    }
    private void savePhysicalAction(String pref, int source, int action) {
        if (action != RemapPolicy.PASS &&
                physicalSignal(source).startsWith("신호 미등록")) {
            new AlertDialog.Builder(this).setTitle("원본 신호가 없어")
                    .setMessage("먼저 ① 입력 학습으로 실제 물리 버튼 신호를 등록해. " +
                            "M1/M2는 오딘3 순정 설정에서 서로 다른 무기능 게임패드 키로 지정할 수 있어.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        if (source == RemapPolicy.HOME && action != RemapPolicy.PASS &&
                !Prefs.status(this).getBoolean("home_key_observed", false)) {
            new AlertDialog.Builder(this).setTitle("HOME 입력을 확인하지 못했어")
                    .setMessage("Android 시스템이 HOME을 먼저 처리하면 접근성 앱에서 막거나 " +
                            "다른 키로 변경할 수 없어. 실제 HOME 신호가 관측되기 전까지 적용하지 않을게.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        prefs.edit().putInt(pref, action).apply();
        DockAccessibilityService.refreshKeyOptionsIfConnected();
        buildUi();
    }

    private void applyNeutralOemKeys() {
        new AlertDialog.Builder(this).setTitle("M1/M2 입력 분리")
            .setMessage("M1/M2 순정 입력을 BUTTON_1(188), BUTTON_2(189)로 변경해. " +
                "이건 버튼 입력을 다시 주입하는 게 아니라 OEM 게임패드 값을 변경하는 방식이라 " +
                "RetroArch 핫키 누름·해제에 더 적합해. 변경 전 두 값을 백업할게.")
            .setNegativeButton("취소", null)
            .setPositiveButton("적용", (dialog, which) -> io.execute(() -> {
                String result;
                PServerBridge bridge = new PServerBridge();
                int old1 = -1, old2 = -1;
                try {
                    old1 = bridge.readKey(true);
                    old2 = bridge.readKey(false);
                    if (!prefs.contains("oem_m1_backup")) prefs.edit().putInt("oem_m1_backup", old1).commit();
                    if (!prefs.contains("oem_m2_backup")) prefs.edit().putInt("oem_m2_backup", old2).commit();
                    if (!bridge.writeKey(true, 188) || !bridge.writeKey(false, 189))
                        throw new IllegalStateException("적용 후 OEM 값 읽기 검증 실패");
                    // Previously learned scan/device signatures are no longer reliable.
                    clearLearnedOemKeys();
                    DockAccessibilityService.refreshKeyOptionsIfConnected();
                    result = "M1/M2 OEM 키값 188/189 저장 확인. 이전 학습값을 초기화했어. " +
                             "각 버튼을 ① 입력 학습으로 다시 등록하면 돼.";
                } catch (Exception error) {
                    StringBuilder recovery = new StringBuilder();
                    if (old1 >= 0) {
                        try { recovery.append(bridge.restoreKey(true, old1) ? "M1 복원됨; " : "M1 복원 확인 실패; "); }
                        catch (Exception e) { recovery.append("M1 복원 실패: ").append(e.getMessage()).append("; "); }
                    }
                    if (old2 >= 0) {
                        try { recovery.append(bridge.restoreKey(false, old2) ? "M2 복원됨; " : "M2 복원 확인 실패; "); }
                        catch (Exception e) { recovery.append("M2 복원 실패: ").append(e.getMessage()).append("; "); }
                    }
                    result = "적용 실패: " + error.getMessage() + "\n" + recovery;
                }
                final String txt = result;
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("OEM 설정 결과")
                    .setMessage(txt).setPositiveButton("확인", null).show());
            })).show();
    }
    private void resetButtonActions() {
        prefs.edit().putInt(Prefs.BUTTON_M1, 0).putInt(Prefs.BUTTON_M2, 0)
             .putInt(Prefs.BUTTON_BACK, 0).putInt(Prefs.BUTTON_HOME, 0).apply();
        DockAccessibilityService.refreshKeyOptionsIfConnected();
        buildUi();
    }
    private void showLatestKey() {
        if (keyStatus != null)
            keyStatus.setText("최근 입력: " + Prefs.status(this).getString("last_key", "아직 없음") +
                    "\n학습: " + Prefs.status(this).getString("last_learn", "없음") +
                    "\n충돌: " + Prefs.status(this).getString("last_input_error", "없음"));
    }
    /** Never keep stale source identities after OEM remap values change. */
    private void clearLearnedOemKeys() {
        SharedPreferences.Editor e = prefs.edit();
        for (int source = 0; source < 2; source++) {
            String prefix = Prefs.PHYSICAL_PREFIX + source;
            e.remove(prefix + "_key").remove(prefix + "_scan")
                .remove(prefix + "_descriptor");
        }
        e.apply();
    }

    private void restoreOemButtons() {
        if (!prefs.contains("oem_m1_backup") && !prefs.contains("oem_m2_backup")) {
            Toast.makeText(this, "백업한 원래 값이 없어", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("OEM M1/M2 원래 설정")
            .setMessage("이 앱에서 최초 변경 전에 읽은 값을 다시 적용할게.")
            .setNegativeButton("취소", null).setPositiveButton("복구", (d, w) -> io.execute(() -> {
                String msg;
                try {
                    PServerBridge bridge = new PServerBridge();
                    boolean ok = true;
                    if (prefs.contains("oem_m1_backup")) ok &= bridge.restoreKey(true, prefs.getInt("oem_m1_backup", 188));
                    if (prefs.contains("oem_m2_backup")) ok &= bridge.restoreKey(false, prefs.getInt("oem_m2_backup", 189));
                    if (ok) { clearLearnedOemKeys(); DockAccessibilityService.refreshKeyOptionsIfConnected(); }
                    msg = ok ? "원래 값 복구 성공. M1/M2 입력 학습값도 초기화했어." : "일부 값 복구 확인 실패";
                } catch (Exception e) { msg = "복구 실패: " + e.getMessage(); }
                final String t = msg;
                runOnUiThread(() -> Toast.makeText(this, t, Toast.LENGTH_LONG).show());
            })).show();
    }
    private interface PackageSelected { void onSelected(String pkg); }
    private void chooseLaunchPackage(String title, PackageSelected callback) {
        Intent filter = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(filter, 0);
        ArrayList<AppChoice> entries = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (ResolveInfo r : activities) {
            if (r.activityInfo == null || !seen.add(r.activityInfo.packageName)) continue;
            CharSequence label = r.loadLabel(pm);
            entries.add(new AppChoice(label + " · " + r.activityInfo.packageName, r.activityInfo.packageName));
        }
        Collator collator = Collator.getInstance(Locale.KOREAN);
        Collections.sort(entries, (a,b) -> collator.compare(a.label, b.label));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(12), dp(12), dp(12), 0);
        EditText search = new EditText(this);
        search.setHint("앱 검색"); search.setSingleLine(true);
        layout.addView(search);
        android.widget.ListView list = new android.widget.ListView(this);
        ArrayAdapter<AppChoice> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>(entries));
        list.setAdapter(adapter);
        layout.addView(list, new LinearLayout.LayoutParams(-1, dp(370)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(layout)
            .setNegativeButton("취소", null).create();
        list.setOnItemClickListener((parent,v,position,id) -> {
            AppChoice selected = adapter.getItem(position);
            if (selected != null) callback.onSelected(selected.pkg);
            dialog.dismiss();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                String q=s.toString().toLowerCase(Locale.ROOT);
                adapter.clear();
                for(AppChoice entry: entries) if(entry.label.toLowerCase(Locale.ROOT).contains(q)) adapter.add(entry);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable e){}
        });
        dialog.show();
    }

    private void copyDiagnostics() {
        StringBuilder out = new StringBuilder("Gil Odin Control 3 · 0.7.0-studio\n");
        out.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" / API ")
            .append(android.os.Build.VERSION.SDK_INT).append('\n');
        out.append("Device: ").append(android.os.Build.MANUFACTURER).append(' ')
            .append(android.os.Build.MODEL).append('\n');
        out.append("Accessibility: ").append(isServiceEnabled()).append('\n');
        out.append("Dock enabled: ").append(prefs.getBoolean(Prefs.ENABLED, false)).append('\n');
        out.append("Dock service alive: ").append(Prefs.status(this).getBoolean("dock_service_alive", false)).append('\n');
        out.append("Dock heartbeat: ").append(Prefs.status(this).getLong("dock_heartbeat", 0)).append('\n');
        out.append("Dock service destroyed at: ").append(Prefs.status(this).getLong("dock_service_destroyed_at", 0)).append('\n');
        out.append("Keepalive status: ").append(Prefs.status(this).getString("keepalive_status", "unknown")).append('\n');
        out.append("Last connected: ").append(Prefs.status(this).getLong("dock_service_connected_at", 0)).append('\n');
        out.append("Last error: ").append(Prefs.status(this).getString("last_error", "none")).append('\n');
        out.append("Foreground: ").append(Prefs.status(this).getString("last_foreground", "unknown")).append('\n');
        out.append("Latest key: ").append(Prefs.status(this).getString("last_key", "no event")).append('\n');
        out.append("Last learn: ").append(Prefs.status(this).getString("last_learn", "none")).append('\n');
        out.append("Input collision: ").append(Prefs.status(this).getString("last_input_error", "none")).append('\n');
        out.append("Clear recent: ").append(Prefs.status(this).getString("last_clear", "none")).append('\n');
        for (int i=0; i<4; i++) out.append("Physical ").append(i).append(": ").append(physicalSignal(i)).append('\n');
        out.append("PServer: ").append(new PServerBridge().status()).append('\n');
        out.append("Profile status: ").append(prefs.getString("profile_status", "none")).append('\n');
        for (int i = 0; i < Prefs.MAX_SLOTS; ++i)
            out.append("Slot ").append(i + 1).append(": ")
                .append(prefs.getString(Prefs.SLOT_PREFIX + i, "")).append('\n');
        try {
            java.io.File crash = new java.io.File(getFilesDir(), "gil-last-crash.txt");
            if (crash.isFile()) {
                String reportText = new String(java.nio.file.Files.readAllBytes(crash.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
                out.append("\n--- LAST PROCESS CRASH ---\n").append(reportText);
            } else {
                out.append("Last process crash: no saved crash report\n");
            }
        } catch (Exception crashReadError) {
            out.append("Last process crash read error: ")
                    .append(crashReadError.getClass().getSimpleName()).append('\n');
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GilOdinControl3", out.toString()));
        Toast.makeText(this, "진단 정보 복사됨", Toast.LENGTH_SHORT).show();
    }

    private int dp(float n) { return Design.dp(this, n); }
    private void margin(View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) v.getLayoutParams();
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        v.setLayoutParams(p);
    }
    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(18), dp(18), dp(18), dp(18));
        p.setBackground(Design.bg(Design.PANEL, Design.PANEL2, 20, this));
        root.addView(p, new LinearLayout.LayoutParams(-1, -2));
        margin(p, 0, 0, 0, 14);
        return p;
    }
    private void section(String kicker, String title) {
        TextView k = Design.text(this, kicker, 11, Design.CYAN, true);
        k.setLetterSpacing(.12f);
        root.addView(k);
        margin(k, 0, 15, 0, 4);
        TextView t = Design.text(this, title, 20, Design.TEXT, true);
        root.addView(t);
        margin(t, 0, 0, 0, 12);
    }
    private Button button(String text, boolean accent) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(accent ? Design.BG : Design.TEXT);
        b.setBackground(Design.bg(accent ? Design.CYAN : Design.PANEL2, 0, 12, this));
        b.setPadding(dp(12), dp(7), dp(12), dp(7));
        b.setMinHeight(dp(45));
        b.setElevation(0f);
        b.setStateListAnimator(null);
        return b;
    }
    private void addSwitch(LinearLayout parent, String title, String desc, String prefKey, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Design.text(this, title, 15, Design.TEXT, true));
        TextView hint = Design.text(this, desc, 12, Design.DIM, false);
        text.addView(hint);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(prefKey, defaultValue));
        sw.setOnCheckedChangeListener((v, enabled) -> {
            prefs.edit().putBoolean(prefKey, enabled).apply();
            if (dockPreview != null) dockPreview.invalidate();
            DockAccessibilityService.requestUiRefresh();
            if (Prefs.KEY_FILTER.equals(prefKey) || Prefs.GAME_PASSTHROUGH.equals(prefKey) ||
                    Prefs.RETROARCH_BACK_PASSTHROUGH.equals(prefKey))
                DockAccessibilityService.refreshKeyOptionsIfConnected();
            if (Prefs.ENABLED.equals(prefKey) && enabled && !isServiceEnabled()) {
                Toast.makeText(this, "먼저 Gil Odin Control 3 접근성을 허용해 줘", Toast.LENGTH_LONG).show();
                Launch.accessibility(this);
            }
        });
        row.addView(sw);
        parent.addView(row);
        margin(row, 0, 3, 0, 20);
    }
    private void slider(LinearLayout parent, String name, String key, int min, int max, int def, String suffix) {
        TextView label = Design.text(this, "", 14, Design.TEXT, true);
        parent.addView(label);
        SeekBar bar = new SeekBar(this);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Design.CYAN));
            bar.setThumbTintList(android.content.res.ColorStateList.valueOf(Design.CYAN));
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Design.PANEL2));
        }
        bar.setMax(max - min);
        bar.setProgress(Prefs.clamp(prefs.getInt(key, def), min, max) - min);
        label.setText(name + " · " + (bar.getProgress() + min) + suffix);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar v, int value, boolean fromUser) {
                label.setText(name + " · " + (value + min) + suffix);
                if (fromUser) {
                    prefs.edit().putInt(key, value + min).apply();
                    refreshPreview();
                }
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(bar);
        margin(bar, 0, 0, 0, 12);
    }
    private void quickButton(LinearLayout p, String name, String detail, Runnable click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Design.text(this, name, 15, Design.TEXT, true));
        t.addView(Design.text(this, detail, 12, Design.DIM, false));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = Design.text(this, "›", 26, Design.CYAN, true);
        r.addView(arrow);
        r.setPadding(dp(2), dp(11), dp(2), dp(11));
        r.setOnClickListener(v -> click.run());
        p.addView(r);
    }

    private static final class AppChoice {
        final String label;
        final String pkg;
        AppChoice(String label, String pkg) { this.label = label; this.pkg = pkg; }
        @Override public String toString() { return label; }
    }

    private void refreshSlots() {
        if (slotsArea == null) return;
        slotsArea.removeAllViews();
        for (int i = 0; i < Prefs.MAX_SLOTS; ++i) {
            final int index = i;
            String pkg = prefs.getString(Prefs.SLOT_PREFIX + i, "");
            String label = "비어 있음";
            if (pkg != null && !pkg.isEmpty()) {
                try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
                catch (Exception e) { label = pkg; }
            }
            quickButton(slotsArea, (i + 1) + "번 슬롯 · " + label, "누르면 앱을 선택하거나 제거", () -> chooseApp(index));
        }
        quickButton(slotsArea, "아이콘 순서 변경", "앱 슬롯 두 개를 선택해 위치 교환", this::moveSlot);
    }

    private void chooseApp(int index) {
        Intent filter = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(filter, 0);
        ArrayList<AppChoice> choices = new ArrayList<>();
        choices.add(new AppChoice("✕  이 슬롯 비우기", ""));
        ArrayList<AppChoice> entries = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (ResolveInfo r : activities) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (pkg == null || !seen.add(pkg)) continue;
            CharSequence s = r.loadLabel(pm);
            entries.add(new AppChoice((s == null ? pkg : s.toString()) + "  ·  " + pkg, pkg));
        }
        Collator korean = Collator.getInstance(Locale.KOREAN);
        Collections.sort(entries, (a, b) -> korean.compare(a.label, b.label));
        choices.addAll(entries);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(8), dp(14), 0);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("앱 이름 검색");
        layout.addView(search);
        android.widget.ListView list = new android.widget.ListView(this);
        ArrayAdapter<AppChoice> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>(choices));
        list.setAdapter(adapter);
        layout.addView(list, new LinearLayout.LayoutParams(-1, dp(420)));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle((index + 1) + "번 독 슬롯")
            .setView(layout)
            .setNegativeButton("취소", (d, which) -> { })
            .create();
        list.setOnItemClickListener((parent, row, position, id) -> {
            AppChoice selected = adapter.getItem(position);
            if (selected != null) {
                prefs.edit().putString(Prefs.SLOT_PREFIX + index, selected.pkg).apply();
                DockAccessibilityService.requestUiRefresh();
                refreshSlots();
                dialog.dismiss();
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int ct, int after) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                String needle = s.toString().toLowerCase(Locale.ROOT).trim();
                adapter.clear();
                for (AppChoice c : choices) if (c.label.toLowerCase(Locale.ROOT).contains(needle)) adapter.add(c);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.show();
    }
}
