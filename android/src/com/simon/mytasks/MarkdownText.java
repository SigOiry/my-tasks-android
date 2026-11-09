package com.simon.mytasks;
import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
/** Android rendering adapter; deliberately cannot produce URLSpan or ImageSpan. */
final class MarkdownText {
 static Spanned render(String markdown){SafeMarkdown.Document d=SafeMarkdown.parse(markdown);SpannableString text=new SpannableString(d.text);for(SafeMarkdown.Style s:d.styles){if(s.start>=s.end)continue;Object span=s.kind.equals("code")?new TypefaceSpan("monospace"):new StyleSpan(s.kind.equals("bold")?Typeface.BOLD:Typeface.ITALIC);text.setSpan(span,s.start,s.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}return text;}
}
