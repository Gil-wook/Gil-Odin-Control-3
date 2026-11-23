from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
m=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
app=m.find('application')
assert app.attrib[a+'name']=='.OdinToolsApplication'
services={s.attrib[a+'name']: s for s in app.findall('service')}
assert 'dev.gil.odincontrol3.DockAccessibilityService' in services
assert '.service.ForegroundAppWatcherService' in services
assert 'dev.gil.odincontrol3.DockKeepAliveService' in services
assert '.tiles.ControllerTileService' in services
assert '.tiles.L2R2TileService' in services
launcher=[]
for activity in app.findall('activity'):
    for category in activity.findall('intent-filter/category'):
        if category.attrib.get(a+'name')=='android.intent.category.LAUNCHER':
            launcher.append(activity.attrib[a+'name'])
assert launcher==['dev.gil.odincontrol3.MainActivity'],launcher
source=(root/'app/src/main/java/de/langerhans/odintools')
expected=['data/AppDatabase.kt','data/AppOverrideDao.kt','main/MainViewModel.kt','main/MainActivity.kt',
          'service/ForegroundAppWatcherService.kt','models/ControllerStyle.kt','models/FanMode.kt',
          'models/PerfMode.kt','models/L2R2Style.kt','tools/BatteryLevelReceiver.kt','tools/ShellExecutor.kt',
          'tools/SettingsRepo.kt','tools/VideoOutputReceiver.kt','tiles/ControllerTileService.kt','tiles/L2R2TileService.kt']
for path in expected:assert (source/path).exists(),path
settings=(source/'tools/SettingsRepo.kt').read_text()
assert 'KEY_CUSTOM_M1_VALUE = "remap_custom_to_m1_value"' in settings
assert 'KEY_CUSTOM_M2_VALUE = "remap_custom_to_m2_value"' in settings
assert 'enableA11yService()\n' not in settings.split('fun applyRequiredSettings()',1)[1].split('}',1)[0]
strings=ET.parse(root/'app/src/main/res/values/strings.xml').getroot()
ko=ET.parse(root/'app/src/main/res/values-ko/strings.xml').getroot()
keys={e.attrib['name'] for e in strings if e.tag=='string'}
ko_keys={e.attrib['name'] for e in ko if e.tag=='string'}
assert ko_keys==keys,('missing translations',keys-ko_keys)
java=root/'app/src/main/java/dev/gil/odincontrol3'
for name in ['DockAccessibilityService.java','DockKeepAliveService.java','PhysicalInputRouter.java','MainActivity.java']:
 assert (java/name).exists()
activity=(java/'MainActivity.java').read_text()
assert 'de.langerhans.odintools.main.MainActivity' in activity
assert 'buildOdinToolsSection' not in activity
assert 'ProfileManager' not in activity
assert not (java/'ProfileManager.java').exists()
assert 'RETROARCH_BACK_PASSTHROUGH' in activity
assert 'buildDockStudioPage()' in activity and 'buildButtonStudioPage()' in activity
assert 'addColorPalette(' in activity and 'chooseOemKeycode(' in activity
assert 'captureOemKeycode(' in activity and 'setOnKeyListener' in activity
preview=(java/'DockPreviewView.java').read_text()
assert 'getApplicationIcon(pkg)' in preview and 'clipPath(clip)' in preview
service=(java/'DockAccessibilityService.java').read_text()
assert 'requestUiRefresh()' in service and 'externalUiRefresh' in service
assert 'boolean nativeBack' in service and 'roots.add(0, active);' in service and 'window.getRoot()' in service
assert 'profileManager.onForeground' not in service # no second OEM profile engine
print('[PASS] One APK, Odin3Tools source, Korean resources, Dock Studio, live app-icon preview and button studio')
