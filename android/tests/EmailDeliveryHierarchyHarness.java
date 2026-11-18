package com.simon.mytasks;

import java.util.ArrayList;
import java.util.List;
import org.json.*;

/** Actual render/delivery/recipients/signature/close/capture/put injected at run time.
 * EmailReplyState is compiled unchanged, not replaced by a hand-written predicate.
 * Synthetic fixtures only.
 * Views, style roles and controller callbacks are recording host seams: no Android
 * pixels, touch reachability, native runtime or provider delivery are proved here.
 */
public class EmailDeliveryHierarchyHarness {
    static int assertions,cases;
    static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    static class View {
        static final int ACCESSIBILITY_LIVE_REGION_POLITE=1,IMPORTANT_FOR_ACCESSIBILITY_NO=2;
        Object params;
        int[] padding;
        View() {}
        View(Activity a) {}
        void setPadding(int l,int t,int r,int b) { padding=new int[]{l,t,r,b}; }
        void setBackground(Object background) {}
        void setImportantForAccessibility(int value) {}
    }
    static class LinearLayout extends View {
        final List<View> children=new ArrayList<>();
        static class LayoutParams {
            final int width,height; final float weight;
            LayoutParams(int w,int h) { this(w,h,0); }
            LayoutParams(int w,int h,float v) { width=w;height=h;weight=v; }
        }
        void addView(View v) { children.add(v); }
        void addView(View v,LayoutParams p) { v.params=p;addView(v); }
        void removeAllViews() { children.clear(); }
        void setGravity(int value) {}
    }
    static class ScrollView extends LinearLayout {
        ScrollView(Activity a) {}
        int getScrollY() { return 0; }
    }
    static class TextView extends View {
        final String text; boolean selectable;
        TextView(String text) { this.text=text; }
        void setTextIsSelectable(boolean value) { selectable=value; }
        void setAccessibilityLiveRegion(int value) {}
    }
    static class EditText extends TextView {
        EditText(String text) { super(text); }
        int getSelectionStart() { return 0; }
        int getSelectionEnd() { return 0; }
        int getScrollY() { return 0; }
        boolean hasFocus() { return false; }
        CharSequence getText() { return text; }
    }
    static class Button extends TextView {
        String role="ordinary"; final Runnable callback;
        Button(String label,Runnable callback) { super(label);this.callback=callback; }
        void click() { callback.run(); }
    }
    static class Style {
        final int surface=3;
        void primary(Button b) { b.role="primary"; }
        void quiet(Button b) { b.role="quiet"; }
        void selected(Button b,boolean selected) {}
        Object shape(int color,int border,int radius) { return new Object(); }
        Button icon(int id,String label,Runnable callback) { return new Button(label,callback); }
    }
    static class R { static class drawable { static final int ic_more=1; } }
    static class Gravity { static final int TOP=48; }
    static class Configuration { float fontScale=1; }
    static class Resources {
        final Configuration config=new Configuration();
        Configuration getConfiguration() { return config; }
    }
    static class Activity {
        final Style ui=new Style(); final int muted=1,ink=2;
        final Resources resources=new Resources();
        EmailDeliveryHierarchyHarness emailUi;
        int renders;
        Resources getResources() { return resources; }
        void render() { renders++; }
        int dp(int value) { return value; }
        LinearLayout vertical() { return new LinearLayout(); }
        LinearLayout horizontal() { return new LinearLayout(); }
        TextView heading(String text,int size) { return new TextView(text); }
        TextView label(String text,int size,int color) { return new TextView(text); }
        Button button(String text,Runnable callback) { return new Button(text,callback); }
        Button primary(String text,Runnable callback) { Button b=button(text,callback);ui.primary(b);return b; }
        void add(LinearLayout parent,View view) { parent.addView(view,new LinearLayout.LayoutParams(-1,-2)); }
        void space(LinearLayout parent,int height) { parent.addView(new View(),new LinearLayout.LayoutParams(-1,height)); }
    }
    static class Controller {
        final EmailReplyState state=new EmailReplyState(new JSONObject());
        final List<String> actions=new ArrayList<>();
        void reconcile() { actions.add("reconcile"); }
        void flush() { actions.add("flush"); }
        void close() { actions.add("close"); }
        void preview() { actions.add("preview"); }
        void send() { actions.add("send"); }
    }
    final Activity a=new Activity();
    final Controller c=new Controller();
    LinearLayout parent=a.vertical(),content=a.vertical(),history,navigationTabs,workspacePanes,
                 editorPane,assistantPane,headerRow,assistantComposer;
    Button editorAssistantButton,emailTab,assistantTab,hideAssistant,reviewButton,askButton,
           recipientsButton,retryButton;
    View hingeGap;
    EditText body,instruction;
    ScrollView editorScroll,assistantScroll;
    TextView status,recipientSummary,assistantFootnote;
    Button[] modes;
    boolean binding,lastSplit,hingeSplit;
    int hingeLeft;
    String boundDraft="",signature="",historySignature="";
    int editorRoutes,assistantRoutes,sourceRoutes,reviewRoutes,statusUpdates;
    EmailDeliveryHierarchyHarness() { a.emailUi=this; }

    // Out-of-scope Android geometry/IME/status/editor collaborators are host seams.
    // They do not select the route: the complete production render() does that.
    boolean keyboardVisible() { return false; }
    void fitPane() {}
    boolean split() { return false; }
    void menu() { throw new AssertionError("Unexpected menu action"); }
    void showEditor() { throw new AssertionError("Unexpected editor action"); }
    void openAssistant(String intent) { throw new AssertionError("Unexpected assistant action"); }
    void sourceChooser(JSONObject sources) { sourceRoutes++; }
    void review(JSONObject preview) { reviewRoutes++; }
    void editor(LinearLayout pane,JSONObject draft) { editorRoutes++; }
    void assistant(LinearLayout pane,JSONObject draft) { assistantRoutes++; }
    void updateStatus() { statusUpdates++; }
    void reflow(boolean follow) {}
    void restore() {}
    void keyboardChanged(boolean visible) {}
    void restoreKeyboard() {}

    // ACTUAL_METHODS

    static void uncertainHierarchy() throws Exception {
        cases++;
        EmailDeliveryHierarchyHarness ui=new EmailDeliveryHierarchyHarness();
        ui.delivery(new JSONObject().put("status","uncertain"));
        check(ui.content.children.size()==3,"uncertain: scroll followed by two footer actions");
        Button recovery=(Button)ui.content.children.get(1),done=(Button)ui.content.children.get(2);
        check(recovery.text.equals("Check delivery · no resend") && recovery.role.equals("primary"),
              "uncertain: Check delivery must stay the first primary footer action");
        check(done.text.equals("Done"),"uncertain: Done label/order must remain unchanged");
        check(done.role.equals("ordinary"),"uncertain: Done must be ordinary secondary, not primary");
        int primaryCount=0;
        for(View v:ui.content.children) if(v instanceof Button && ((Button)v).role.equals("primary"))primaryCount++;
        check(primaryCount==1,"uncertain: exactly one primary footer action");
    }
    static void terminalHierarchy(String status,String heading,String guidance) throws Exception {
        cases++;
        EmailDeliveryHierarchyHarness ui=new EmailDeliveryHierarchyHarness();
        ui.delivery(new JSONObject().put("status",status));
        check(ui.content.children.size()==2,status+": scroll and Done only; no reconcile/send");
        Button done=(Button)ui.content.children.get(1);
        check(done.text.equals("Done") && done.role.equals("primary"),status+": Done remains primary");
        LinearLayout receipt=(LinearLayout)((ScrollView)ui.content.children.get(0)).children.get(0);
        check(((TextView)receipt.children.get(0)).text.equals(heading),status+": heading unchanged");
        check(((TextView)receipt.children.get(1)).text.equals(guidance),status+": read-only/no-resend guidance unchanged");
    }
    static void routedRecoveryAndCallbacks(String status,boolean pending,boolean recovery) throws Exception {
        cases++;
        EmailDeliveryHierarchyHarness ui=new EmailDeliveryHierarchyHarness();
        JSONObject draft=new JSONObject().put("id","synthetic-"+status).put("status",status).put("body","Saved body");
        ui.c.state.data.put("draft",draft).put("send_pending",pending);
        check(!ui.c.state.editable(),status+": actual state must be read-only");
        String before=ui.c.state.data.toString();
        ui.render();
        check(ui.c.actions.isEmpty() && ui.a.renders==0,status+": render invokes no controller action or Close");
        check(before.equals(ui.c.state.data.toString()),status+": render does not mutate saved state");
        check(ui.editorRoutes==0 && ui.assistantRoutes==0 && ui.sourceRoutes==0 && ui.reviewRoutes==0,
              status+": actual render must dispatch delivery, not another workspace");
        check(!ui.binding && ui.statusUpdates==1,status+": delivery route completes binding/status update");
        check(ui.content.children.size()==(recovery?3:2),status+": actual routed footer count");
        check(ui.content.children.get(0) instanceof ScrollView,status+": reading region precedes footer");
        Button done=(Button)ui.content.children.get(ui.content.children.size()-1);
        check(done.text.equals("Done") && done.role.equals(recovery?"ordinary":"primary"),status+": routed Done role");
        if(recovery) {
            Button checkDelivery=(Button)ui.content.children.get(1);
            check(checkDelivery.text.equals("Check delivery · no resend") && checkDelivery.role.equals("primary"),
                  status+": routed recovery is primary and first");
            checkDelivery.click();
            check(ui.c.actions.equals(java.util.Arrays.asList("reconcile")),status+": Check only reconciles, never sends/closes");
            check(ui.a.emailUi==ui && ui.a.renders==0,status+": Check does not leave route");
        }
        ui.c.actions.clear();
        // A retained preview is removed by actual close(); no email-send callback is used.
        ui.c.state.data.put("preview",new JSONObject().put("confirmation_id","synthetic-only"));
        done.click();
        check(ui.c.actions.equals(java.util.Arrays.asList("flush","close")),status+": Done retains actual Close flush/close order, never reconciles/sends");
        check(!ui.c.state.data.has("preview") && ui.a.emailUi==null && ui.a.renders==1,status+": actual Close clears preview and exits once");
    }
    static void editableDraftRouteControl() throws Exception {
        cases++;
        EmailDeliveryHierarchyHarness ui=new EmailDeliveryHierarchyHarness();
        ui.c.state.data.put("draft",new JSONObject().put("id","editable-control").put("status","draft"));
        check(ui.c.state.editable(),"ordinary draft remains editable without send_pending");
        ui.render();
        check(ui.editorRoutes==1 && ui.assistantRoutes==1 && ui.reviewRoutes==0 && ui.sourceRoutes==0,
              "ordinary draft must dispatch editor/assistant, not delivery");
        check(ui.content.children.size()==1 && !(ui.content.children.get(0) instanceof ScrollView),
              "ordinary draft has workspace panes, not delivery/footer");
        check(ui.c.actions.isEmpty() && ui.a.renders==0,"editable route render has no callbacks");
    }
    static String repeat(String value,int count) {
        StringBuilder result=new StringBuilder();
        for(int i=0;i<count;i++)result.append(value);
        return result.toString();
    }
    static void layout(View view,int width,int height,float weight,String message) {
        check(view.params instanceof LinearLayout.LayoutParams,message+": explicit linear layout params");
        LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)view.params;
        check(params.width==width && params.height==height && params.weight==weight,message);
    }
    static void longExactReceipt(String status,boolean pending,String heading,String guidance,boolean recovery) throws Exception {
        cases++;
        String from="Élodie 東京 <owner@example.test> "+repeat("é界",90);
        String to1="Zoë <first@example.test> "+repeat("受信者",90),to2="ثانٍ <second@example.test>";
        String cc="Kārlis <copy@example.test> "+repeat("copie 📨 ",70);
        String subject="Re: 条件 — "+repeat("résumé 👩🏽‍💻 e\u0301 ",130)+"SUBJECT-END";
        String text="  Original leading spaces\r\n"+repeat("🧑🏾‍🔬 café e\u0301 — 東京 العربية\n\n",260)+"\r\nTAIL: retain every condition ✅  ";
        String error="Readback only: "+repeat("確認できません — réessayer sans renvoi\n",80)+"ERROR-END";
        String excerpt=repeat("Original message evidence — 你好\n",100)+"SOURCE-END";
        JSONObject source=new JSONObject().put("id","synthetic-exact-source-α").put("excerpt",excerpt);
        JSONObject draft=new JSONObject().put("id","long-"+status).put("status",status)
            .put("from",from).put("to",new JSONArray().put(to1).put(to2)).put("cc",new JSONArray().put(cc))
            .put("subject",subject).put("body",text).put("error",error).put("source",source);
        EmailDeliveryHierarchyHarness ui=new EmailDeliveryHierarchyHarness();
        // A different local body must not replace the immutable saved delivery copy.
        ui.c.state.data.put("draft",draft).put("send_pending",pending).put("body","LOCAL TEXT IS NOT THE RECEIPT");
        String before=ui.c.state.data.toString();
        ui.render();
        ScrollView scroll=(ScrollView)ui.content.children.get(0);
        layout(scroll,-1,0,1,status+": receipt keeps weighted full-width scroll region");
        check(scroll.children.size()==1,status+": single receipt reading subtree");
        LinearLayout receipt=(LinearLayout)scroll.children.get(0);
        check(java.util.Arrays.equals(receipt.padding,new int[]{8,12,8,16}),status+": receipt padding unchanged (identity-dp host seam)");
        List<TextView> labels=new ArrayList<>();
        for(View child:receipt.children) {
            check(!(child instanceof EditText) && !(child instanceof Button),status+": receipt read-only, actions outside scroll");
            if(child instanceof TextView)labels.add((TextView)child);
        }
        String[] expected={heading,guidance,"From  "+from+"\nTo  "+to1+", "+to2+"\nCc  "+cc,subject,text,error};
        check(labels.size()==expected.length,status+": no dropped or extra receipt text");
        for(int i=0;i<expected.length;i++)check(labels.get(i).text.equals(expected[i]),status+": exact Unicode receipt text at index "+i);
        check(labels.get(4).selectable,status+": full immutable body remains selectable");
        check(ui.content.children.size()==(recovery?3:2),status+": footer stays outside reading subtree");
        for(int i=1;i<ui.content.children.size();i++)layout(ui.content.children.get(i),-1,-2,0,status+": footer action full-width/wrap-content");
        check(ui.c.actions.isEmpty() && ui.a.renders==0,status+": long-content render invokes no action");
        check(before.equals(ui.c.state.data.toString()),status+": source identity/excerpt and saved reply remain exact");
        ui.render();
        check(ui.content.children.size()==(recovery?3:2) && ui.c.actions.isEmpty(),status+": repeated render neither duplicates footer nor invokes callbacks");
    }
    public static void main(String[] args) throws Exception {
        uncertainHierarchy();
        terminalHierarchy("sent","Reply sent","Your reply was sent. This saved copy is read-only.");
        terminalHierarchy("sending","Sending reply…","Please wait. Do not send this reply again.");
        routedRecoveryAndCallbacks("uncertain",false,true);
        routedRecoveryAndCallbacks("draft",true,true);
        routedRecoveryAndCallbacks("sent",false,false);
        routedRecoveryAndCallbacks("sending",true,false);
        editableDraftRouteControl();
        String recoveryGuidance="The result is not confirmed. Check delivery before taking any further action; this reply will not be automatically resent.";
        longExactReceipt("uncertain",false,"Delivery needs checking",recoveryGuidance,true);
        longExactReceipt("draft",true,"Delivery needs checking",recoveryGuidance,true);
        longExactReceipt("sent",false,"Reply sent","Your reply was sent. This saved copy is read-only.",false);
        longExactReceipt("sending",true,"Sending reply…","Please wait. Do not send this reply again.",false);
        System.out.println("EmailDeliveryHierarchyHarness passed: "+cases+" cases, "+assertions+" assertions; actual methods, host UI seams only (not native proof)");
    }
}
