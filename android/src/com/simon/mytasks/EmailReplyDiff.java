package com.simon.mytasks;
final class EmailReplyDiff{static CharSequence render(String old,String proposed){return "Removed: [-text-] · Added: {+text+}\n\n"+EmailReplyDiffPolicy.compare(old,proposed);}}
