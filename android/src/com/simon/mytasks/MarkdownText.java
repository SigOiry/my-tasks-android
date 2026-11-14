package com.simon.mytasks;
import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
/** Android rendering adapter; deliberately cannot produce URLSpan or ImageSpan. */
final class MarkdownText {
 static Spanned render(String markdown){return render(SafeMarkdown.parse(markdown));}
 static Spanned render(SafeMarkdown.Document d){SpannableString text=new SpannableString(d.text);for(SafeMarkdown.Style s:d.styles){if(s.start>=s.end||s.kind.startsWith("web:"))continue;Object span=s.kind.equals("code")?new TypefaceSpan("monospace"):new StyleSpan(s.kind.equals("bold")?Typeface.BOLD:Typeface.ITALIC);text.setSpan(span,s.start,s.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}return text;}
}
