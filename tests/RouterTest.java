package dev.gil.odincontrol3;

public final class RouterTest {
    static void ok(boolean value,String why) { if(!value)throw new AssertionError(why); }
    static PhysicalInputRouter.Event event(int code,int scan,int device,int action,int repeat) {
        return new PhysicalInputRouter.Event(code,scan,device,"od3-controller",action,repeat);
    }
    public static void main(String[] args) {
        PhysicalInputRouter.Binding[] bindings = {
            new PhysicalInputRouter.Binding(98,700,"od3-controller"),
            new PhysicalInputRouter.Binding(101,701,"od3-controller"),
            new PhysicalInputRouter.Binding(4,702,"od3-controller"),
            new PhysicalInputRouter.Binding(3,703,"od3-controller")
        };
        int[] actions={RemapPolicy.DO_BACK,RemapPolicy.PASS,RemapPolicy.DO_HOME,RemapPolicy.BLOCK};
        PhysicalInputRouter router=new PhysicalInputRouter();
        PhysicalInputRouter.Decision m1=router.route(event(98,700,4,0,0),bindings,actions,false,false);
        ok(m1.source==0 && m1.consume && m1.fire==RemapPolicy.DO_BACK,"M1 only mapped to back");
        PhysicalInputRouter.Decision repeat=router.route(event(98,700,4,0,1),bindings,actions,false,false);
        ok(repeat.consume && repeat.fire==RemapPolicy.PASS,"key auto repeat cannot re-fire");
        ok(router.route(event(98,700,4,1,0),bindings,actions,false,false).consume,"M1 UP consumed");
        ok(!router.route(event(98,700,4,1,0),bindings,actions,false,false).consume,"duplicate UP ignored");
        PhysicalInputRouter.Decision m2=router.route(event(101,701,4,0,0),bindings,actions,false,false);
        ok(m2.source==1 && !m2.consume,"M2 native hardware DOWN passed");
        ok(!router.route(event(101,701,4,1,0),bindings,actions,false,false).consume,"M2 native hardware UP passed");
        PhysicalInputRouter.Decision back=router.route(event(4,702,4,0,0),bindings,actions,false,false);
        ok(back.source==2 && back.fire==RemapPolicy.DO_HOME,"physical back -> home");
        router.route(event(4,702,4,1,0),bindings,actions,false,false);
        PhysicalInputRouter.Decision home=router.route(event(3,703,4,0,0),bindings,actions,false,false);
        ok(home.source==3 && home.consume && home.fire==RemapPolicy.BLOCK,"physical home blocked when observed");
        router.route(event(3,703,4,1,0),bindings,actions,false,false);
        ok(!PhysicalInputRouter.canBind(0,bindings[2],bindings),"cannot learn M1 as physical BACK");
        ok(!PhysicalInputRouter.canBind(0,new PhysicalInputRouter.Binding(4,-1,""),bindings),"wildcard conflicts");
        ok(PhysicalInputRouter.canBind(0,new PhysicalInputRouter.Binding(188,0,"od3-controller"),bindings),"separate OEM key allowed");
        PhysicalInputRouter.Binding[] collision=bindings.clone();
        collision[1]=bindings[0];
        ok(PhysicalInputRouter.identify(event(98,700,4,0,0),collision)==PhysicalInputRouter.AMBIGUOUS,"ambiguous source disabled");
        router.reset();
        PhysicalInputRouter.Decision gameHold=router.route(event(98,700,4,0,0),bindings,actions,true,true);
        ok(!gameHold.consume && gameHold.fire==0,"RetroArch hotkey DOWN passed");
        ok(!router.route(event(101,701,4,0,0),bindings,actions,true,true).consume,"RetroArch chord second button passed");
        ok(!router.route(event(98,700,4,1,0),bindings,actions,false,false).consume,"game hold UP passed even when state changes");
        ok(!router.route(event(101,701,4,1,0),bindings,actions,false,false).consume,"game chord UP passed");
        // Explicit remapping inside a game works by default.
        router.reset();
        PhysicalInputRouter.Decision mappedInGame = router.route(
            event(98,700,4,0,0), bindings,actions,true,false);
        ok(mappedInGame.consume && mappedInGame.fire==RemapPolicy.DO_BACK,
            "configured M1 mapping honored in game unless pass-through selected");
        ok(router.route(event(98,700,4,1,0),bindings,actions,true,false).consume,
            "original UP still consumed for remapped game button");
        // RetroArch BACK may be configured for independent native hotkey use.
        router.reset();
        PhysicalInputRouter.Decision retroBack = router.route(
            event(4,702,4,0,0),bindings,actions,true,false,true);
        ok(!retroBack.consume && retroBack.fire==RemapPolicy.PASS,
            "RetroArch hotkey modifier BACK native DOWN preserved");
        ok(!router.route(event(4,702,4,1,0),bindings,actions,false,false).consume,
            "RetroArch BACK UP preserved even after foreground changes");
        router.reset();
        ok(RecentAppsPolicy.clearButtonLabel("모두 지우기"),"Korean clear-all recognized");
        ok(RecentAppsPolicy.recentsMarker("com.android.launcher3:id/recents_view"),"recents id recognized");
        ok(!RecentAppsPolicy.recentsMarker("org.example:id/home_button"),"home not recents");
        ok(!RecentAppsPolicy.clearButtonLabel("설정"),"avoid arbitrary click");
        System.out.println("Input router and recents tests passed");
    }
}
